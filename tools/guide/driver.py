"""Обходит приложение на эмуляторе и снимает скриншоты для руководства пользователя.
Временный инструмент (ветка со скриншотами, не для main)."""
import os, re, subprocess, sys, time
import xml.etree.ElementTree as ET

OUT = sys.argv[1] if len(sys.argv) > 1 else "tools/guide/out"
os.makedirs(OUT, exist_ok=True)
PKG = "com.lesovod.mobile"
LOG = open(os.path.join(OUT, "_log.txt"), "a")


def log(*a):
    s = " ".join(str(x) for x in a)
    print(s, flush=True)
    LOG.write(s + "\n"); LOG.flush()


def sh(*args, out=False):
    r = subprocess.run(["adb", *args], capture_output=True, timeout=60)
    return r.stdout if out else r.returncode


def dump():
    for _ in range(4):
        sh("shell", "uiautomator", "dump", "/sdcard/u.xml")
        x = sh("exec-out", "cat", "/sdcard/u.xml", out=True)
        if x.strip().startswith(b"<?xml"):
            try:
                return ET.fromstring(x)
            except ET.ParseError:
                pass
        time.sleep(1)
    return None


def nodes(root):
    return list(root.iter("node")) if root is not None else []


def center(n):
    a = list(map(int, re.findall(r"\d+", n.get("bounds"))))
    return (a[0] + a[2]) // 2, (a[1] + a[3]) // 2


def find(text=None, contains=None, desc=None, cls=None, idx=0, root=None):
    root = root if root is not None else dump()
    hits = []
    for n in nodes(root):
        t = n.get("text") or ""
        d = n.get("content-desc") or ""
        if text is not None and t != text and d != text:
            continue
        if contains is not None and contains.lower() not in (t + " " + d).lower():
            continue
        if desc is not None and desc not in d:
            continue
        if cls is not None and n.get("class") != cls:
            continue
        hits.append(n)
    return hits[idx] if len(hits) > idx else None


def tap_xy(x, y, wait=1.5):
    sh("shell", "input", "tap", str(x), str(y)); time.sleep(wait)


def tap(text=None, contains=None, desc=None, idx=0, wait=2.0, required=False):
    for _ in range(3):
        n = find(text=text, contains=contains, desc=desc, idx=idx)
        if n is not None:
            # Compose: кликабельным бывает предок — тапаем по центру самого узла
            tap_xy(*center(n), wait=wait)
            log("tap", text or contains or desc)
            return True
        time.sleep(1.5)
    log("NOT FOUND", text or contains or desc)
    if required:
        shot("_notfound_" + re.sub(r"\W+", "_", str(text or contains or desc)))
    return False


def type_into(field_text, value, idx=0):
    n = find(text=field_text, idx=idx) or find(contains=field_text, idx=idx)
    if n is None:
        log("field not found", field_text); return False
    tap_xy(*center(n), wait=0.8)
    sh("shell", "input", "text", value); time.sleep(0.8)
    return True


def back(wait=1.5):
    sh("shell", "input", "keyevent", "4"); time.sleep(wait)


def hide_kb():
    sh("shell", "input", "keyevent", "111"); time.sleep(0.6)


def swipe_up(times=1):
    for _ in range(times):
        sh("shell", "input", "swipe", "540", "1700", "540", "700", "400"); time.sleep(1.2)


def shot(name, wait=1.0):
    time.sleep(wait)
    png = sh("exec-out", "screencap", "-p", out=True)
    open(os.path.join(OUT, name + ".png"), "wb").write(png)
    x = sh("exec-out", "cat", "/sdcard/u.xml", out=True) if dump() is not None else b""
    open(os.path.join(OUT, name + ".xml"), "wb").write(x)
    log("shot", name)


def dismiss_dialogs():
    for t in ("While using the app", "При использовании приложения", "Allow", "Разрешить", "Только в этот раз", "Only this time"):
        n = find(text=t)
        if n is not None:
            tap_xy(*center(n)); log("dismissed", t)


def start_app():
    sh("shell", "am", "force-stop", PKG)
    sh("shell", "monkey", "-p", PKG, "-c", "android.intent.category.LAUNCHER", "1")
    time.sleep(6)
    dismiss_dialogs()


def login(user, pin, name_prefix=None):
    start_app()
    if name_prefix:
        shot(name_prefix + "-vhod")
    type_into("Логин", user)
    type_into("PIN-код", pin)
    hide_kb()
    if name_prefix:
        shot(name_prefix + "-vhod-zapolnen")
    tap("Войти", wait=5)
    dismiss_dialogs()
    time.sleep(2)
    dismiss_dialogs()


def to_nav():
    """Вернуться на экран с нижней панелью (не выходя из приложения)."""
    for _ in range(3):
        if find(text="Остатки") is not None and find(text="Заметки") is not None:
            return
        back()
    if find(text="Заметки") is None:
        log("relaunch")
        start_app()


def nav(tab, wait=2.0):
    to_nav()
    return tap(tab, wait=wait)


def safe(fn):
    try:
        fn()
    except Exception as e:  # noqa
        log("STEP FAILED", fn.__name__, repr(e))


def logout():
    sh("shell", "pm", "clear", PKG); time.sleep(2)
    for p in ("ACCESS_FINE_LOCATION", "ACCESS_COARSE_LOCATION", "POST_NOTIFICATIONS"):
        sh("shell", "pm", "grant", PKG, "android.permission." + p)


# ---------------- сценарий ----------------
def master_flow():
    login("master", "1111", name_prefix="m01")
    shot("m02-smena")
    swipe_up(); shot("m02b-smena-niz")
    # отметка на смене
    if tap("Отметиться", wait=2.5):
        shot("m03-otmetka")
    # остатки: «Мои делянки» → одно касание
    if nav("Остатки", wait=4):
        shot("m04-moi-delyanki")
        if tap(contains="кв.43 выд.3", wait=4):
            shot("m06-ostatki-rezultat")
            swipe_up(); shot("m07-ostatki-porody")
            swipe_up(); shot("m08-ostatki-porody2")
    if nav("Остатки", wait=4):
        if not tap(contains="кв.35 выд.7", wait=4):
            swipe_up(); tap(contains="кв.35 выд.7", wait=4)
        shot("m09-ostatki-pererub")
    if nav("Кубатурник"):
        shot("m10-kubaturnik")
    if nav("Карта", wait=10):
        dismiss_dialogs(); time.sleep(6)
        shot("m11-karta")
        if tap(contains="Слои") or tap(desc="Слои"):
            shot("m12-karta-sloi"); back()
        if tap(contains="Инструменты") or tap(desc="Инструменты"):
            shot("m13-karta-instrumenty"); back()
    if nav("Заметки"):
        shot("m14-zametki")
    for t, nm in (("Отчёт", "m16-otchet"), ("Инвентаризация лесных культур", "m17-inventarizatsiya"),
                  ("Перевод лесных культур", "m18-perevod"), ("Табель — ручной ввод", "m19-tabel"),
                  ("Уведомления", "m20-uvedomleniya"), ("Профиль", "m21-profil")):
        if nav("Ещё"):
            if nm == "m16-otchet":
                shot("m15-eshche")
            if tap(t, wait=3) or (swipe_up() or tap(t, wait=3)):
                shot(nm)
    # профиль: тема, отправка данных, «Подготовиться к выезду», руководство
    if nav("Ещё") and tap("Профиль", wait=3):
        swipe_up(); shot("m21b-profil-niz")
        if tap("Подготовиться к выезду", wait=12):
            shot("m21c-podgotovka")
        if tap("Светлая", wait=2):
            to_nav(); shot("m23-svetlaya-tema")
            nav("Ещё"); tap("Профиль", wait=3); tap("Тёмная", wait=2)
    # без связи: остатки из сохранённого
    sh("shell", "svc", "wifi", "disable"); sh("shell", "svc", "data", "disable"); time.sleep(3)
    if nav("Остатки", wait=4):
        shot("m22-ostatki-bez-svyazi")
        if tap(contains="кв.43 выд.3", wait=3):
            shot("m22b-ostatki-bez-svyazi-detal")
    sh("shell", "svc", "wifi", "enable"); sh("shell", "svc", "data", "enable"); time.sleep(4)


def worker_flow():
    logout()
    login("valshik1", "3333")
    shot("w01-smena-valshik")
    if tap("Ещё"):
        shot("w02-eshche-valshik")
        if tap("Проба рубок ухода", wait=3):
            shot("w03-proba")
        if nav("Ещё") and tap("Отчёт", wait=3):
            shot("w04-otchet")


def trakt_flow():
    logout()
    login("trakt1", "6666")
    shot("t01-smena-traktorist")


if __name__ == "__main__":
    for p in ("ACCESS_FINE_LOCATION", "ACCESS_COARSE_LOCATION", "POST_NOTIFICATIONS"):
        sh("shell", "pm", "grant", PKG, "android.permission." + p)
    sh("emu", "geo", "fix", "30.2395", "54.4470")
    for f in (master_flow, worker_flow, trakt_flow):
        safe(f)
    log("done")
