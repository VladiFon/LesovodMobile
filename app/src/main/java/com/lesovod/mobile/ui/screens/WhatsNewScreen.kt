package com.lesovod.mobile.ui.screens

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Route
import androidx.compose.material.icons.rounded.WbSunny
import androidx.compose.material.icons.rounded.Directions
import androidx.compose.material.icons.rounded.Timeline
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.HelpOutline
import androidx.compose.material.icons.rounded.Chat
import androidx.compose.material.icons.rounded.Park
import androidx.compose.material.icons.rounded.Explore
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.Layers
import androidx.compose.material.icons.rounded.Map
import androidx.compose.material.icons.rounded.SystemUpdate
import androidx.compose.material.icons.rounded.TextIncrease
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lesovod.mobile.BuildConfig
import com.lesovod.mobile.ui.components.LvBigButton
import com.lesovod.mobile.ui.components.LvTopBar
import com.lesovod.mobile.ui.components.UpdateBanner
import com.lesovod.mobile.ui.theme.LocalLv
import com.lesovod.mobile.ui.theme.Manrope

/** Запоминает, показан ли «Что нового» для текущей версии (показываем один раз после обновления). */
object WhatsNew {
    private const val PREFS = "whats_new"
    private const val KEY = "seen_version"

    fun shouldShow(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, null) != BuildConfig.VERSION_NAME

    fun markSeen(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY, BuildConfig.VERSION_NAME).apply()
    }
}

/** Куда ведёт «Показать, где это». */
enum class WhatsNewTarget { MORE, PROFILE, MAP }

private data class NewsCard(val icon: ImageVector, val title: String, val text: String, val target: WhatsNewTarget?)

private val NEWS = listOf(
    NewsCard(
        Icons.Rounded.Layers,
        "Карта без каши из лесосек",
        "На одном месте теперь один контур: лесные культуры рисуются сверху, лесосека на культурах — тонким пунктиром, выдел под своей лесосекой не заливается второй раз. Закрытые делянки и то, что убрали на сайте или в QGIS, на карте не видно. Мастер, помощник и лесничий могут убрать лишнюю делянку прямо из карточки: «Скрыть делянку с карт».",
        WhatsNewTarget.MAP,
    ),
    NewsCard(
        Icons.Rounded.Route,
        "След: назад к машине",
        "На карте «+» → «Записать след». Голубой пунктир покажет, где вы прошли. Заплутали, жмите «Назад по следу», и телефон поведёт обратно к началу. След хранится только у вас; на сервер уйдёт, если сами нажмёте «Сохранить».",
        WhatsNewTarget.MAP,
    ),
    NewsCard(
        Icons.Rounded.WbSunny,
        "Режим «Солнце»",
        "На ярком солнце или снегу экран не видно? Солнышко вверху карты: белый фон, чёрные буквы, всё чётко. Нажмёте ещё раз, и вернётся прежняя тема. Есть и в Профиле.",
        WhatsNewTarget.MAP,
    ),
    NewsCard(
        Icons.Rounded.Directions,
        "Навигатор по лесным дорогам",
        "«Вести сюда» теперь строит путь по дорогам, а не по прямой. Выберите, на чём едете: УАЗ, лесовоз или трактор. «Поехали» подскажет крупно: через 200 м направо. «Отправить» скинет маршрут коллеге.",
        WhatsNewTarget.MAP,
    ),
    NewsCard(
        Icons.Rounded.Timeline,
        "Мастера рисуют дороги",
        "Мастер, помощник и лесничий: «Инструменты» на карте → «Нарисовать дорогу». Тыкайте по карте или включите «Записать проездом» и просто проезжайте. Дорогу сразу видят все.",
        WhatsNewTarget.MAP,
    ),
    NewsCard(
        Icons.Rounded.Chat,
        "Заметки как переписка",
        "Сообщения конторе теперь лентой, как в мессенджере: ответ виден сразу под вопросом. Внизу готовые фразы и кнопка «где я».",
        null,
    ),
    NewsCard(
        Icons.Rounded.Park,
        "Инвентаризация по шагам",
        "Участок, пробы, потом кликер: зелёная кнопка — живое, красная — погибло, приживаемость считается на ходу. В конце проверка и «Отправить».",
        WhatsNewTarget.MORE,
    ),
    NewsCard(
        Icons.Rounded.Shield,
        "Сторож границы",
        "Откройте делянку на карте и нажмите «Сторож границы». Подойдёте к краю ближе 20 м, и телефон завибрирует и покажет крупно, сколько метров осталось.",
        WhatsNewTarget.MAP,
    ),
    NewsCard(
        Icons.Rounded.Explore,
        "Азимут и SOS",
        "«Вести сюда» теперь с кнопкой «Азимут»: стрелка по компасу и подсказка, если ушли в сторону. Красная SOS слева внизу карты: держите 3 секунды, и мастер получит ваше место.",
        WhatsNewTarget.MAP,
    ),
    NewsCard(
        Icons.Rounded.Map,
        "Карта: «Где я» и «Что рядом»",
        "Сверху крупно видно, в каком вы квартале и выделе. Кнопка «+» внизу: метка, фото, отчёт отсюда и список всего, что рядом, со стрелкой, куда идти.",
        WhatsNewTarget.MAP,
    ),
    NewsCard(
        Icons.Rounded.Layers,
        "Слои одним нажатием",
        "Делянки, выдела, культуры, метки и пробы включаются чипами прямо под «Где я». На карточке выдела большие кнопки «Вести сюда» и «Отчёт отсюда».",
        WhatsNewTarget.MAP,
    ),
    NewsCard(
        Icons.Rounded.Apps,
        "«Ещё» разложено по полочкам",
        "Отчёты, лес и личное, каждое своим цветом. Есть поиск «Что ищем?». Остатки теперь тоже тут.",
        WhatsNewTarget.MORE,
    ),
    NewsCard(
        Icons.Rounded.HelpOutline,
        "Кнопка «?» на экранах",
        "Нажмите «?» в правом углу, и телефон простыми словами расскажет, что тут делают.",
        null,
    ),
    NewsCard(
        Icons.Rounded.TextIncrease,
        "Режим «Крупно»",
        "В Профиле можно включить «Крупно»: все буквы и кнопки станут больше. Удобно без очков и в перчатках.",
        WhatsNewTarget.PROFILE,
    ),
    NewsCard(
        Icons.Rounded.SystemUpdate,
        "Обновления ставятся сами",
        "Нажали «Обновить», дальше телефон всё скачает и поставит. На Wi-Fi может обновиться даже сам.",
        null,
    ),
)

/** Экран «Что нового» (прототип «Что нового»): плашка обновления, карточки новинок, «Понятно, к работе». */
@Composable
fun WhatsNewScreen(onBack: () -> Unit, onShow: (WhatsNewTarget) -> Unit) {
    val context = LocalContext.current
    val lv = LocalLv.current
    LaunchedEffect(Unit) { WhatsNew.markSeen(context) }
    Column(Modifier.fillMaxSize().background(lv.bg)) {
        LvTopBar(
            title = "Что нового",
            onBack = onBack,
            help = "Тут коротко, что поменялось в версии ${BuildConfig.VERSION_NAME}. Нажмите «Показать, где это», и откроется нужный экран.",
        )
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            UpdateBanner()
            Text(
                "Версия ${BuildConfig.VERSION_NAME}",
                color = lv.tx2,
                style = TextStyle(fontFamily = Manrope, fontWeight = FontWeight.Bold, fontSize = 15.sp),
            )
            NEWS.forEach { card ->
                Column(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(lv.sf).padding(16.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(44.dp).clip(RoundedCornerShape(14.dp)).background(lv.gS), contentAlignment = Alignment.Center) {
                            Icon(card.icon, contentDescription = null, tint = lv.gT, modifier = Modifier.size(26.dp))
                        }
                        Spacer(Modifier.width(12.dp))
                        Text(
                            card.title,
                            color = lv.tx,
                            style = TextStyle(fontFamily = Manrope, fontWeight = FontWeight.ExtraBold, fontSize = 18.sp, lineHeight = 22.sp),
                        )
                    }
                    Text(
                        card.text,
                        color = lv.tx2,
                        style = TextStyle(fontFamily = Manrope, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, lineHeight = 22.sp),
                        modifier = Modifier.padding(top = 10.dp),
                    )
                    card.target?.let { t ->
                        Text(
                            "Показать, где это →",
                            color = lv.gT,
                            style = TextStyle(fontFamily = Manrope, fontWeight = FontWeight.ExtraBold, fontSize = 16.sp),
                            modifier = Modifier.padding(top = 6.dp).clip(RoundedCornerShape(10.dp)).clickable { onShow(t) }.padding(vertical = 8.dp),
                        )
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
        }
        LvBigButton(
            text = "Понятно, к работе",
            onClick = onBack,
            modifier = Modifier.padding(16.dp),
        )
    }
}
