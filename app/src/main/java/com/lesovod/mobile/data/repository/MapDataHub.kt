package com.lesovod.mobile.data.repository

import android.content.Context
import com.lesovod.mobile.data.local.MapCache
import com.lesovod.mobile.data.local.MapPrefs
import com.lesovod.mobile.data.network.NetworkModule
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.floor

data class DownloadState(
    val lesnichestvo: String? = null,
    val running: Boolean = false,
    val done: Int = 0,
    val total: Int = 0,
    val error: String? = null,
) {
    val fraction: Float get() = if (total == 0) 0f else done.toFloat() / total
}

/**
 * Общий для карты и профиля доступ к данным + скачивание карты лесничества. Загрузка живёт
 * в собственном scope: не обрывается, когда пользователь уходит с экрана карты.
 */
class MapDataHub private constructor(context: Context) {
    val cache = MapCache(context)
    val repository = MapRepository(NetworkModule.api, cache)
    val prefs = MapPrefs(context)

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var job: Job? = null

    private val _download = MutableStateFlow(DownloadState())
    val download: StateFlow<DownloadState> = _download.asStateFlow()

    /**
     * Скачивает на устройство всё, что нужно карте без интернета: кварталы, лесосеки и выделы (с их
     * таксационными границами) — только в тех ячейках сетки, где реально есть кварталы лесничества.
     */
    fun downloadLesnichestvo(name: String, num: Int) {
        if (_download.value.running) return
        job = scope.launch {
            _download.value = DownloadState(lesnichestvo = name, running = true)
            try {
                val kvartaly = repository.getKvartaly(num, force = true).getOrElse {
                    _download.value = DownloadState(lesnichestvo = name, error = it.message)
                    return@launch
                }
                repository.getLesoseki(num, force = true)

                val cells = LinkedHashSet<Triple<CellTier, Int, Int>>()
                for (shape in kvartaly) for (tier in CellTier.entries) {
                    val ixs = floor(shape.minLon / tier.degrees).toInt()..floor(shape.maxLon / tier.degrees).toInt()
                    val iys = floor(shape.minLat / tier.degrees).toInt()..floor(shape.maxLat / tier.degrees).toInt()
                    for (ix in ixs) for (iy in iys) cells.add(Triple(tier, ix, iy))
                }

                _download.update { it.copy(done = 2, total = cells.size + 2) }
                val failed = AtomicInteger(0)
                val permits = Semaphore(2)
                coroutineScope {
                    cells.map { (tier, ix, iy) ->
                        async {
                            permits.withPermit {
                                if (repository.getVydelaCell(num, tier, ix, iy, force = true).isFailure) failed.incrementAndGet()
                                _download.update { it.copy(done = it.done + 1) }
                            }
                        }
                    }.awaitAll()
                }

                if (failed.get() == 0) {
                    prefs.setLastDownloadAt(name, System.currentTimeMillis())
                    _download.value = DownloadState(lesnichestvo = name)
                } else {
                    _download.value = DownloadState(lesnichestvo = name, error = "Не удалось загрузить ${failed.get()} из ${cells.size} участков — повторите при связи")
                }
            } catch (e: CancellationException) {
                _download.value = DownloadState(lesnichestvo = name)
                throw e
            } catch (e: Throwable) {
                _download.value = DownloadState(lesnichestvo = name, error = e.message ?: "Не удалось скачать карту")
            }
        }
    }

    fun cancelDownload() {
        job?.cancel()
    }

    companion object {
        @Volatile
        private var instance: MapDataHub? = null

        fun getInstance(context: Context): MapDataHub =
            instance ?: synchronized(this) {
                instance ?: MapDataHub(context.applicationContext).also { instance = it }
            }
    }
}
