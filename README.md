# TakaTrim

TakaTrim calculates the exact amount you need to transfer from bKash to your bank so your balance becomes zero, or as close to zero as possible.

It comes as a standalone Windows app (**TakaTrim.exe**, no installation needed) and an Android app (`TakaTrimMobile`).

## Features

* Calculate exact bKash to Bank transfer amount
* Supports bKash NPSB transfer calculation
* Supports regular bKash to Bank transfer calculation
* Editable fee settings, including fee rounding mode
* Accepts balances typed as `1,000.50`, `৳ 940.39` or `940.39 Tk`
* Copy calculated amount with one click
* Instant results, even for very large balances
* Works offline

## How to Use

1. Open `TakaTrim.exe`
2. Enter your current bKash balance
3. Select the transfer type
4. Click **Calculate amount**
5. Copy the transfer amount
6. Enter that amount in your bKash app

## Fee rounding

bKash fees often have more than 2 decimals (for example 4.27635 Tk). **Round normally** turns that into 4.28; **Cut extra decimals** turns it into 4.27. Both usually reach a zero balance, but the transfer amount can differ by 1 paisa. If bKash shows a fee 1 paisa different from TakaTrim, switch the rounding mode.

## Build the Windows EXE

```powershell
py -m pip install PySide6 pyinstaller
.\build_exe.ps1
```

The EXE is written to `TakaTrim.exe` in the main folder (a working copy also stays in `dist\`).

Run the calculator tests:

```powershell
py bkash_zero_balance_desktop_app.py --test
```

## Android app

**TakaTrim.apk** in the main folder is a ready-to-install debug build. Copy it to your phone and open it (allow "Install unknown apps" for your file manager or browser when asked).

Rebuild it (also runs the calculator unit tests):

```powershell
.\build_apk.ps1
```

Or open `TakaTrimMobile` in Android Studio and run the `app` configuration. Calculator unit tests are in `app/src/test` (`TakaTrimCalculatorTest`).

## Important Note

Always check the final fee preview inside the official bKash app before confirming a transfer. Fees and rounding behavior may change.
