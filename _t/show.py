import pathlib
l = pathlib.Path(r"D:\桌面\SJ Lens\app\src\main\java\com\sj\followclip\ui\ledger\LedgerScreen.kt")
lines = l.read_text(encoding="utf-8").split("\n")
print("--- 440..485 ---")
for i in range(438, min(len(lines), 486)):
    print(i+1, repr(lines[i]))
