r"""
TakaTrim

Professional standalone Windows desktop app version.

This version uses PySide6, so the EXE opens as a real app window.
It does NOT use browser, tkinter, input(), sys.exit(), or raise SystemExit().

Install needed packages:
    py -m pip install PySide6 pyinstaller

Build standalone EXE (bundles the window icon and sets the EXE icon):
    .\build_exe.ps1

Your EXE will be here:
    dist\TakaTrim.exe

Run tests:
    python bkash_zero_balance_desktop_app.py --test

Command-line examples still work:
    python bkash_zero_balance_desktop_app.py --balance 940.39 --method npsb
    python bkash_zero_balance_desktop_app.py --balance 6637.70 --method bank
"""

from decimal import Decimal, InvalidOperation, ROUND_HALF_UP, ROUND_DOWN, getcontext
import os
import sys
import unittest

getcontext().prec = 28
CENT = Decimal("0.01")

METHOD_NPSB = "npsb"
METHOD_BANK = "bank"
ROUND_NORMAL = "round"
ROUND_CUT_DOWN = "down"

VALID_METHODS = {METHOD_NPSB, METHOD_BANK}
VALID_ROUNDING_MODES = {ROUND_NORMAL, ROUND_CUT_DOWN}


class CalculatorError(ValueError):
    """User-facing calculator error."""


MAX_AMOUNT = Decimal("100000000")  # 10 crore Tk; far above any real bKash balance


def to_decimal(value):
    """Convert input to a finite Decimal, accepting "1,000", "Tk" and "৳"."""
    if isinstance(value, Decimal):
        number = value
    else:
        text = str(value).strip()
        for junk in (",", "৳", "Tk", "tk", "TK", " "):
            text = text.replace(junk, "")
        if not text:
            raise CalculatorError("Value cannot be empty.")
        try:
            number = Decimal(text)
        except InvalidOperation as exc:
            raise CalculatorError(f"Invalid number: {str(value).strip()}") from exc

    if not number.is_finite():
        raise CalculatorError(f"Invalid number: {value}")
    if abs(number) > MAX_AMOUNT:
        raise CalculatorError("Number is too large.")
    return number


def money(value):
    """Normalize a value to 2 decimal places."""
    return to_decimal(value).quantize(CENT, rounding=ROUND_HALF_UP)


def fee_setting(value, name):
    """Parse a fee setting. Keeps full precision (e.g. 1.255%) and rejects negatives."""
    number = to_decimal(value)
    if number < 0:
        raise CalculatorError(f"{name} cannot be negative.")
    return number


def round_fee(value, rounding_mode=ROUND_NORMAL):
    """Round or cut fee to 2 decimal places."""
    value = to_decimal(value)

    if rounding_mode == ROUND_CUT_DOWN:
        return value.quantize(CENT, rounding=ROUND_DOWN)

    if rounding_mode == ROUND_NORMAL:
        return value.quantize(CENT, rounding=ROUND_HALF_UP)

    raise CalculatorError("Invalid rounding mode. Use 'round' or 'down'.")


def calculate_fee(
    amount,
    method,
    npsb_per_1000="8.50",
    bank_percent="1.25",
    min_fee="10.00",
    rounding_mode=ROUND_NORMAL,
):
    """Calculate bKash fee for a transfer amount."""
    amount = money(amount)

    if amount < 0:
        raise CalculatorError("Transfer amount cannot be negative.")

    if method == METHOD_NPSB:
        raw_fee = amount * fee_setting(npsb_per_1000, "NPSB fee") / Decimal("1000")
        return round_fee(raw_fee, rounding_mode)

    if method == METHOD_BANK:
        raw_fee = amount * fee_setting(bank_percent, "Bank transfer fee %") / Decimal("100")
        fee = round_fee(raw_fee, rounding_mode)
        return max(fee, money(fee_setting(min_fee, "Minimum fee")))

    raise CalculatorError("Invalid method. Use 'npsb' or 'bank'.")


def find_transfer_amount(
    balance,
    method,
    npsb_per_1000="8.50",
    bank_percent="1.25",
    min_fee="10.00",
    rounding_mode=ROUND_NORMAL,
):
    """
    Find the highest transfer amount that does not exceed balance after fee.

    Returns a dictionary:
        transfer  - amount to enter in bKash
        fee       - calculated bKash fee
        total     - transfer + fee
        remaining - balance - total
        exact     - True when remaining is exactly 0.00

    Fee settings are non-negative, so the fee never decreases as the transfer
    grows, and transfer + fee is strictly increasing paisa by paisa. That makes
    a binary search over every paisa exact (same answer as checking each one)
    while staying instant for large balances.
    """
    balance = money(balance)

    if balance <= 0:
        raise CalculatorError("Balance must be greater than 0.")

    if method not in VALID_METHODS:
        raise CalculatorError("Invalid method. Use 'npsb' or 'bank'.")

    def total_for(cents):
        transfer = Decimal(cents) / Decimal("100")
        fee = calculate_fee(
            transfer,
            method,
            npsb_per_1000=npsb_per_1000,
            bank_percent=bank_percent,
            min_fee=min_fee,
            rounding_mode=rounding_mode,
        )
        return transfer, fee, transfer + fee

    best = None
    low, high = 0, int(balance * 100)
    while low <= high:
        mid = (low + high) // 2
        transfer, fee, total = total_for(mid)
        if total <= balance:
            best = (transfer, fee, total)
            low = mid + 1
        else:
            high = mid - 1

    if best is not None:
        transfer, fee, total = best
        return {
            "transfer": money(transfer),
            "fee": money(fee),
            "total": money(total),
            "remaining": money(balance - total),
            "exact": total == balance,
        }

    return {
        "transfer": money("0"),
        "fee": money("0"),
        "total": money("0"),
        "remaining": balance,
        "exact": False,
    }


def format_result(result):
    status = "Exact zero match found" if result["exact"] else "Closest safe amount found"
    return (
        f"{status}\n"
        f"{'-' * len(status)}\n"
        f"Transfer amount : {result['transfer']:.2f} Tk\n"
        f"Fee             : {result['fee']:.2f} Tk\n"
        f"Total deducted  : {result['total']:.2f} Tk\n"
        f"Remaining       : {result['remaining']:.2f} Tk"
    )


def resource_path(relative_path):
    """Return a file path that works before and after PyInstaller builds the EXE."""
    base_path = getattr(sys, "_MEIPASS", os.path.dirname(os.path.abspath(__file__)))
    return os.path.join(base_path, relative_path)


def help_text():
    return """TakaTrim

Double-click the EXE to open the standalone desktop app.

Command-line usage, optional:
  python bkash_zero_balance_desktop_app.py --balance AMOUNT [options]
  python bkash_zero_balance_desktop_app.py --test

Options:
  --balance AMOUNT       Current bKash balance, example: 940.39
  --method METHOD        npsb or bank. Default: npsb
  --npsb-fee AMOUNT      NPSB fee per 1000 Tk. Default: 8.50
  --bank-percent PCT     Bank transfer fee percentage. Default: 1.25
  --min-fee AMOUNT       Bank transfer minimum fee. Default: 10.00
  --rounding MODE        round or down. Default: round
  --help                 Show this help
  --test                 Run tests

Examples:
  python bkash_zero_balance_desktop_app.py --balance 940.39 --method npsb
  python bkash_zero_balance_desktop_app.py --balance 6637.70 --method bank
"""


def parse_cli_args(argv):
    """
    Minimal CLI parser that never raises SystemExit.

    Returns:
        (args, error_message)
    """
    args = {
        "test": False,
        "help": False,
        "balance": None,
        "method": METHOD_NPSB,
        "npsb_fee": "8.50",
        "bank_percent": "1.25",
        "min_fee": "10.00",
        "rounding": ROUND_NORMAL,
        "gui": False,
    }

    i = 0
    while i < len(argv):
        token = argv[i]

        if token == "--test":
            args["test"] = True
            i += 1
            continue

        if token in ("--help", "-h"):
            args["help"] = True
            i += 1
            continue

        if token == "--gui":
            args["gui"] = True
            i += 1
            continue

        if token in ("--balance", "--method", "--npsb-fee", "--bank-percent", "--min-fee", "--rounding"):
            if i + 1 >= len(argv):
                return None, f"Missing value after {token}"
            value = argv[i + 1]
            if token == "--balance":
                args["balance"] = value
            elif token == "--method":
                args["method"] = value.lower()
            elif token == "--npsb-fee":
                args["npsb_fee"] = value
            elif token == "--bank-percent":
                args["bank_percent"] = value
            elif token == "--min-fee":
                args["min_fee"] = value
            elif token == "--rounding":
                args["rounding"] = value.lower()
            i += 2
            continue

        return None, f"Unknown argument: {token}"

    if args["method"] not in VALID_METHODS:
        return None, "Invalid method. Use 'npsb' or 'bank'."

    if args["rounding"] not in VALID_ROUNDING_MODES:
        return None, "Invalid rounding. Use 'round' or 'down'."

    return args, None


def run_cli(args):
    result = find_transfer_amount(
        args["balance"],
        args["method"],
        npsb_per_1000=args["npsb_fee"],
        bank_percent=args["bank_percent"],
        min_fee=args["min_fee"],
        rounding_mode=args["rounding"],
    )
    print("\n" + format_result(result))
    return result


def run_gui_app():
    """Run the real desktop GUI. PySide6 is imported lazily for test/CLI safety."""
    try:
        from PySide6.QtCore import QEasingCurve, QPoint, QPropertyAnimation, Qt, QTimer
        from PySide6.QtGui import QGuiApplication, QIcon
        from PySide6.QtWidgets import (
            QApplication,
            QComboBox,
            QFrame,
            QGraphicsOpacityEffect,
            QHBoxLayout,
            QLabel,
            QLineEdit,
            QMainWindow,
            QMessageBox,
            QPushButton,
            QSizePolicy,
            QSpacerItem,
            QVBoxLayout,
            QWidget,
        )
    except ModuleNotFoundError:
        print(
            "PySide6 is not installed. Install it with:\n"
            "  py -m pip install PySide6\n\n"
            "Then build the EXE with:\n"
            "  py -m PyInstaller --onefile --noconsole --name TakaTrim bkash_zero_balance_desktop_app.py",
            file=sys.stderr,
        )
        return 1

    class StatCard(QFrame):
        def __init__(self, label, value="0.00 Tk", accent=False):
            super().__init__()
            self.setObjectName("AccentStatCard" if accent else "StatCard")
            self.setSizePolicy(QSizePolicy.Expanding, QSizePolicy.Fixed)
            layout = QVBoxLayout(self)
            layout.setContentsMargins(16, 16, 16, 16)
            layout.setSpacing(6)
            self.setMinimumHeight(92)

            self.label = QLabel(label)
            self.label.setObjectName("StatLabel")
            self.value = QLabel(value)
            self.value.setObjectName("AccentStatValue" if accent else "StatValue")
            self.value.setTextInteractionFlags(Qt.TextSelectableByMouse)

            layout.addWidget(self.label)
            layout.addWidget(self.value)

        def set_value(self, value):
            self.value.setText(value)

    class MainWindow(QMainWindow):
        def __init__(self):
            super().__init__()
            self.setWindowTitle("TakaTrim")
            self.setMinimumSize(980, 760)
            self.resize(1080, 820)
            icon_path = resource_path("app_icon.ico")
            if os.path.exists(icon_path):
                self.setWindowIcon(QIcon(icon_path))

            root = QWidget()
            root.setObjectName("Root")
            self.setCentralWidget(root)
            page = QVBoxLayout(root)
            page.setContentsMargins(24, 24, 24, 24)
            page.setSpacing(18)

            self.build_header(page)
            self.build_main_content(page)
            self.apply_styles()

            self.last_transfer = None
            self.copy_button.setEnabled(False)
            self.build_copy_toast()
            self.balance_input.setFocus()

        def build_copy_toast(self):
            # Small floating "Copied" bubble shown above the Copy button for ~1.5 s.
            self.copy_toast = QLabel(self)
            self.copy_toast.setObjectName("CopyToast")
            self.copy_toast.setAlignment(Qt.AlignCenter)
            self.copy_toast.setAttribute(Qt.WA_TransparentForMouseEvents)
            self.copy_toast.hide()
            self.toast_opacity = QGraphicsOpacityEffect(self.copy_toast)
            self.copy_toast.setGraphicsEffect(self.toast_opacity)
            self.toast_fade = QPropertyAnimation(self.toast_opacity, b"opacity", self)
            self.toast_fade.setDuration(350)
            self.toast_fade.setEasingCurve(QEasingCurve.OutCubic)
            self.toast_fade.finished.connect(self.on_toast_fade_finished)
            self.toast_timer = QTimer(self)
            self.toast_timer.setSingleShot(True)
            self.toast_timer.timeout.connect(self.fade_out_toast)

        def show_copy_toast(self, text):
            self.toast_timer.stop()
            self.toast_fade.stop()
            self.copy_toast.setText(text)
            self.copy_toast.adjustSize()
            self.copy_toast.resize(self.copy_toast.width() + 28, 36)
            button_top_center = self.copy_button.mapTo(self, QPoint(self.copy_button.width() // 2, 0))
            x = button_top_center.x() - self.copy_toast.width() // 2
            x = max(8, min(x, self.width() - self.copy_toast.width() - 8))
            self.copy_toast.move(x, button_top_center.y() - self.copy_toast.height() - 8)
            self.toast_opacity.setOpacity(1.0)
            self.copy_toast.show()
            self.copy_toast.raise_()
            self.toast_timer.start(1500)

        def fade_out_toast(self):
            self.toast_fade.setStartValue(1.0)
            self.toast_fade.setEndValue(0.0)
            self.toast_fade.start()

        def on_toast_fade_finished(self):
            if self.toast_opacity.opacity() <= 0.01:
                self.copy_toast.hide()

        def build_header(self, page):
            header = QFrame()
            header.setObjectName("Hero")
            hero = QHBoxLayout(header)
            hero.setContentsMargins(28, 24, 28, 24)
            hero.setSpacing(18)

            mark = QLabel("৳")
            mark.setObjectName("LogoMark")
            mark.setAlignment(Qt.AlignCenter)
            mark.setFixedSize(64, 64)
            hero.addWidget(mark)

            title_box = QVBoxLayout()
            title_box.setSpacing(5)

            title = QLabel("TakaTrim")
            title.setObjectName("HeroTitle")
            title_box.addWidget(title)

            subtitle = QLabel("Calculate the exact transfer amount before sending money to your bank.")
            subtitle.setObjectName("HeroSubtitle")
            title_box.addWidget(subtitle)

            hero.addLayout(title_box)
            hero.addItem(QSpacerItem(20, 20, QSizePolicy.Expanding, QSizePolicy.Minimum))

            badge = QLabel("OFFLINE APP")
            badge.setObjectName("Badge")
            badge.setAlignment(Qt.AlignCenter)
            badge.setFixedHeight(34)
            hero.addWidget(badge)

            page.addWidget(header)

        def build_main_content(self, page):
            body = QHBoxLayout()
            body.setSpacing(18)
            page.addLayout(body)

            left = QFrame()
            left.setObjectName("Panel")
            left_layout = QVBoxLayout(left)
            left_layout.setContentsMargins(22, 22, 22, 22)
            left_layout.setSpacing(14)
            body.addWidget(left, 5)

            section_title = QLabel("Transfer details")
            section_title.setObjectName("SectionTitle")
            left_layout.addWidget(section_title)

            self.balance_input = QLineEdit()
            self.balance_input.setPlaceholderText("Example: 940.39")
            self.balance_input.returnPressed.connect(self.calculate)
            self.balance_input.setClearButtonEnabled(True)
            self.add_field(left_layout, "Current bKash balance", self.balance_input, "Enter your full available balance")

            self.method_input = QComboBox()
            self.method_input.addItem("NPSB (bKash to Bank)", METHOD_NPSB)
            self.method_input.addItem("Bank Transfer (bKash to Bank)", METHOD_BANK)
            self.add_field(left_layout, "Transfer type", self.method_input, "Choose the transfer option you are using in bKash")

            settings = QFrame()
            settings.setObjectName("InnerPanel")
            settings.setMinimumHeight(0)
            settings.setSizePolicy(QSizePolicy.Expanding, QSizePolicy.Fixed)
            settings_layout = QVBoxLayout(settings)
            settings_layout.setContentsMargins(16, 16, 16, 16)
            settings_layout.setSpacing(10)
            settings_layout.setSizeConstraint(QVBoxLayout.SetFixedSize)
            left_layout.addWidget(settings)

            settings_title = QLabel("Fee settings")
            settings_title.setObjectName("SmallTitle")
            settings_layout.addWidget(settings_title)

            self.npsb_fee_input = QLineEdit("8.50")
            self.bank_percent_input = QLineEdit("1.25")
            self.min_fee_input = QLineEdit("10.00")

            self.rounding_input = QComboBox()
            self.rounding_input.addItem("Round normally", ROUND_NORMAL)
            self.rounding_input.addItem("Cut extra decimals", ROUND_CUT_DOWN)

            self.add_compact_field(settings_layout, "NPSB fee per 1000 Tk", self.npsb_fee_input)
            self.add_compact_field(settings_layout, "Fee rounding", self.rounding_input)
            self.add_compact_field(settings_layout, "Bank transfer fee %", self.bank_percent_input)
            self.add_compact_field(settings_layout, "Bank transfer minimum fee", self.min_fee_input)


            left_layout.addSpacing(18)

            button_row = QHBoxLayout()
            button_row.setSpacing(12)
            self.calculate_button = QPushButton("Calculate amount")
            self.calculate_button.setObjectName("PrimaryButton")
            self.calculate_button.clicked.connect(self.calculate)
            self.copy_button = QPushButton("Copy amount")
            self.copy_button.setObjectName("SecondaryButton")
            self.copy_button.clicked.connect(self.copy_amount)
            button_row.addWidget(self.calculate_button, 2)
            button_row.addWidget(self.copy_button, 1)
            button_container = QFrame()
            button_container.setObjectName("ButtonBar")
            button_container_layout = QVBoxLayout(button_container)
            button_container_layout.setContentsMargins(0, 0, 0, 0)
            button_container_layout.addLayout(button_row)
            left_layout.addWidget(button_container)

            left_layout.addStretch()

            right = QFrame()
            right.setObjectName("Panel")
            right_layout = QVBoxLayout(right)
            right_layout.setContentsMargins(22, 22, 22, 22)
            right_layout.setSpacing(14)
            body.addWidget(right, 4)

            result_title = QLabel("Result")
            result_title.setObjectName("SectionTitle")
            right_layout.addWidget(result_title)

            self.status_label = QLabel("Ready to calculate")
            self.status_label.setObjectName("StatusPill")
            self.status_label.setAlignment(Qt.AlignCenter)
            self.status_label.setFixedHeight(36)
            right_layout.addWidget(self.status_label)

            self.transfer_card = StatCard("Transfer amount", "0.00 Tk", accent=True)
            self.fee_card = StatCard("Fee", "0.00 Tk")
            self.total_card = StatCard("Total deducted", "0.00 Tk")
            self.remaining_card = StatCard("Remaining", "0.00 Tk")
            right_layout.addWidget(self.transfer_card)
            right_layout.addWidget(self.fee_card)
            right_layout.addWidget(self.total_card)
            right_layout.addWidget(self.remaining_card)

            divider = QFrame()
            divider.setFrameShape(QFrame.HLine)
            divider.setObjectName("Divider")
            right_layout.addWidget(divider)

            instruction = QLabel(
                "Use the pink transfer amount in your bKash app. If bKash shows a different fee, adjust the fee settings and calculate again."
            )
            instruction.setObjectName("Instruction")
            instruction.setWordWrap(True)
            right_layout.addWidget(instruction)

            right_layout.addStretch()

        def add_field(self, layout, label_text, widget, hint_text=None):
            label = QLabel(label_text)
            label.setObjectName("FormLabel")
            layout.addWidget(label)
            layout.addWidget(widget)
            if hint_text:
                hint = QLabel(hint_text)
                hint.setObjectName("Hint")
                layout.addWidget(hint)

        def add_compact_field(self, layout, label_text, widget):
            row = QHBoxLayout()
            row.setSpacing(14)
            row.setContentsMargins(0, 0, 0, 0)
            label = QLabel(label_text)
            label.setObjectName("MiniLabel")
            label.setMinimumWidth(220)
            label.setSizePolicy(QSizePolicy.Fixed, QSizePolicy.Fixed)
            widget.setMinimumHeight(40)
            widget.setMaximumHeight(40)
            widget.setSizePolicy(QSizePolicy.Expanding, QSizePolicy.Fixed)
            row.addWidget(label, 0, Qt.AlignVCenter)
            row.addWidget(widget, 1, Qt.AlignVCenter)
            layout.addLayout(row)

        def set_status(self, text, exact=None):
            self.status_label.setText(text)
            self.status_label.setProperty("exact", "" if exact is None else ("true" if exact else "false"))
            self.status_label.style().unpolish(self.status_label)
            self.status_label.style().polish(self.status_label)

        def clear_result(self):
            # Never leave an old amount on screen (or in Copy) after a failed calculation.
            self.last_transfer = None
            self.copy_button.setEnabled(False)
            for card in (self.transfer_card, self.fee_card, self.total_card, self.remaining_card):
                card.set_value("0.00 Tk")
            self.set_status("Ready to calculate")

        def calculate(self):
            try:
                result = find_transfer_amount(
                    self.balance_input.text(),
                    self.method_input.currentData(),
                    npsb_per_1000=self.npsb_fee_input.text(),
                    bank_percent=self.bank_percent_input.text(),
                    min_fee=self.min_fee_input.text(),
                    rounding_mode=self.rounding_input.currentData(),
                )
            except CalculatorError as exc:
                self.clear_result()
                QMessageBox.warning(self, "Invalid input", str(exc))
                return
            except Exception as exc:  # never let a slot exception kill the windowed EXE
                self.clear_result()
                QMessageBox.critical(self, "Unexpected error", f"Could not calculate: {exc}")
                return

            if result["transfer"] <= 0:
                self.clear_result()
                self.set_status("Balance too low for this fee", exact=False)
                self.remaining_card.set_value(f"{result['remaining']:.2f} Tk")
                return

            self.last_transfer = f"{result['transfer']:.2f}"
            self.copy_button.setEnabled(True)
            self.set_status("Exact zero match" if result["exact"] else "Closest safe amount", result["exact"])

            self.transfer_card.set_value(f"{result['transfer']:.2f} Tk")
            self.fee_card.set_value(f"{result['fee']:.2f} Tk")
            self.total_card.set_value(f"{result['total']:.2f} Tk")
            self.remaining_card.set_value(f"{result['remaining']:.2f} Tk")

        def copy_amount(self):
            if not self.last_transfer:
                return
            clipboard = QGuiApplication.clipboard()
            clipboard.setText(self.last_transfer)
            if clipboard.text() == self.last_transfer:
                self.set_status(f"Copied {self.last_transfer} Tk", exact=True)
                self.show_copy_toast(f"✓ Copied {self.last_transfer} Tk")
            else:
                self.set_status("Copy failed, try again", exact=False)
                self.show_copy_toast("Copy failed")

        def apply_styles(self):
            self.setStyleSheet(
                """
                QWidget#Root {
                    background: #f4f6fb;
                    font-family: Segoe UI, Arial, sans-serif;
                    color: #111827;
                }
                QFrame#Hero {
                    background: qlineargradient(x1:0, y1:0, x2:1, y2:0, stop:0 #e2136e, stop:1 #8b5cf6);
                    border-radius: 24px;
                }
                QLabel#LogoMark {
                    background: rgba(255, 255, 255, 0.20);
                    border: 1px solid rgba(255, 255, 255, 0.34);
                    border-radius: 32px;
                    color: white;
                    font-size: 32px;
                    font-weight: 900;
                }
                QLabel#HeroTitle {
                    color: white;
                    font-size: 28px;
                    font-weight: 900;
                    letter-spacing: -0.4px;
                }
                QLabel#HeroSubtitle {
                    color: rgba(255, 255, 255, 0.86);
                    font-size: 14px;
                }
                QLabel#Badge {
                    color: white;
                    background: rgba(255, 255, 255, 0.18);
                    border: 1px solid rgba(255, 255, 255, 0.32);
                    border-radius: 17px;
                    padding-left: 14px;
                    padding-right: 14px;
                    font-size: 12px;
                    font-weight: 800;
                    letter-spacing: 1px;
                }
                QFrame#Panel {
                    background: white;
                    border: 1px solid #e7eaf0;
                    border-radius: 22px;
                }
                QFrame#InnerPanel {
                    background: #f9fafc;
                    border: 1px solid #edf0f5;
                    border-radius: 16px;
                    margin-bottom: 0px;
                }
                QFrame#ButtonBar {
                    background: transparent;
                    border: none;
                    margin-top: 6px;
                }
                QLabel#SectionTitle {
                    color: #101828;
                    font-size: 20px;
                    font-weight: 800;
                }
                QLabel#SmallTitle {
                    color: #101828;
                    font-size: 15px;
                    font-weight: 800;
                }
                QLabel#FormLabel, QLabel#MiniLabel {
                    color: #344054;
                    font-weight: 700;
                    font-size: 13px;
                }
                QLabel#Hint, QLabel#Instruction {
                    color: #667085;
                    font-size: 12px;
                    line-height: 1.35;
                }
                QLineEdit, QComboBox {
                    color: #111827;
                    selection-background-color: #e2136e;
                    selection-color: white;
                    background: white;
                    border: 1px solid #d0d5dd;
                    border-radius: 12px;
                    padding: 10px 12px;
                    font-size: 15px;
                    min-height: 20px;
                }
                QLineEdit:focus, QComboBox:focus {
                    border: 2px solid #e2136e;
                    padding: 9px 11px;
                }
                QComboBox::drop-down {
                    border: 0;
                    width: 30px;
                }
                QComboBox QAbstractItemView {
                    background: white;
                    color: #111827;
                    selection-background-color: #fff1f7;
                    selection-color: #e2136e;
                }
                QPushButton {
                    border: none;
                    border-radius: 13px;
                    padding: 13px 16px;
                    font-size: 15px;
                    font-weight: 800;
                }
                QPushButton#PrimaryButton {
                    background: #e2136e;
                    color: white;
                }
                QPushButton#PrimaryButton:hover {
                    background: #c70f61;
                }
                QPushButton#PrimaryButton:pressed {
                    background: #a90d52;
                }
                QPushButton#SecondaryButton {
                    background: #f2f4f7;
                    color: #344054;
                }
                QPushButton#SecondaryButton:hover {
                    background: #e4e7ec;
                }
                QPushButton#SecondaryButton:disabled {
                    color: #98a2b3;
                }
                QLabel#StatusPill {
                    background: #f2f4f7;
                    color: #344054;
                    border-radius: 18px;
                    font-weight: 800;
                }
                QLabel#StatusPill[exact="true"] {
                    background: #ecfdf3;
                    color: #027a48;
                }
                QLabel#StatusPill[exact="false"] {
                    background: #fff7ed;
                    color: #b45309;
                }
                QFrame#AccentStatCard {
                    background: #fff1f7;
                    border: 1px solid #ffd0e2;
                    border-radius: 18px;
                }
                QFrame#StatCard {
                    background: #f9fafb;
                    border: 1px solid #edf0f5;
                    border-radius: 18px;
                }
                QLabel#StatLabel {
                    color: #667085;
                    font-size: 12px;
                    font-weight: 800;
                    text-transform: uppercase;
                    letter-spacing: 0.6px;
                }
                QLabel#AccentStatValue {
                    color: #e2136e;
                    font-size: 30px;
                    font-weight: 900;
                    letter-spacing: -0.8px;
                }
                QLabel#StatValue {
                    color: #101828;
                    font-size: 20px;
                    font-weight: 800;
                }
                QFrame#Divider {
                    color: #edf0f5;
                    background: #edf0f5;
                    max-height: 1px;
                }
                QMessageBox {
                    background: white;
                }
                QLabel#CopyToast {
                    background: #101828;
                    color: white;
                    border-radius: 18px;
                    font-size: 13px;
                    font-weight: 800;
                }
                """
            )

    app = QApplication.instance() or QApplication(sys.argv[:1])
    # The design is light-only. Without this, Windows dark mode gives Qt a white
    # text palette and typed text becomes invisible on the white input boxes.
    try:
        app.styleHints().setColorScheme(Qt.ColorScheme.Light)
    except AttributeError:  # Qt < 6.8
        pass
    icon_path = resource_path("app_icon.ico")
    if os.path.exists(icon_path):
        app.setWindowIcon(QIcon(icon_path))
    window = MainWindow()
    window.show()
    return app.exec()


class BkashCalculatorTests(unittest.TestCase):
    def test_npsb_known_940_39(self):
        result = find_transfer_amount("940.39", METHOD_NPSB, npsb_per_1000="8.50")
        self.assertEqual(result["transfer"], Decimal("932.46"))
        self.assertEqual(result["fee"], Decimal("7.93"))
        self.assertEqual(result["total"], Decimal("940.39"))
        self.assertTrue(result["exact"])

    def test_npsb_known_507_37_cut_down(self):
        # 503.10 + 4.27 is the "Cut extra decimals" split (4.27635 -> 4.27).
        result = find_transfer_amount("507.37", METHOD_NPSB, npsb_per_1000="8.50", rounding_mode=ROUND_CUT_DOWN)
        self.assertEqual(result["transfer"], Decimal("503.10"))
        self.assertEqual(result["fee"], Decimal("4.27"))
        self.assertEqual(result["total"], Decimal("507.37"))
        self.assertTrue(result["exact"])

    def test_npsb_known_507_37_round(self):
        # Normal rounding (4.27635 -> 4.28) still reaches exactly zero, one paisa lower.
        result = find_transfer_amount("507.37", METHOD_NPSB, npsb_per_1000="8.50")
        self.assertEqual(result["transfer"], Decimal("503.09"))
        self.assertEqual(result["fee"], Decimal("4.28"))
        self.assertTrue(result["exact"])

    def test_bank_transfer_known_6637_70(self):
        result = find_transfer_amount("6637.70", METHOD_BANK, bank_percent="1.25", min_fee="10.00")
        self.assertEqual(result["transfer"], Decimal("6555.75"))
        self.assertEqual(result["fee"], Decimal("81.95"))
        self.assertEqual(result["total"], Decimal("6637.70"))
        self.assertTrue(result["exact"])

    def test_bank_transfer_min_fee_small_balance(self):
        result = find_transfer_amount("31.93", METHOD_BANK, bank_percent="1.25", min_fee="10.00")
        self.assertEqual(result["transfer"], Decimal("21.93"))
        self.assertEqual(result["fee"], Decimal("10.00"))
        self.assertEqual(result["total"], Decimal("31.93"))
        self.assertTrue(result["exact"])

    def test_invalid_method(self):
        with self.assertRaises(CalculatorError):
            calculate_fee("100", "wrong")

    def test_zero_balance_rejected(self):
        with self.assertRaises(CalculatorError):
            find_transfer_amount("0", METHOD_NPSB)

    def test_no_input_mode_not_required(self):
        args, error = parse_cli_args(["--balance", "118", "--method", "npsb"])
        self.assertIsNone(error)
        result = find_transfer_amount(args["balance"], args["method"])
        self.assertEqual(result["transfer"], Decimal("117.01"))
        self.assertEqual(result["fee"], Decimal("0.99"))
        self.assertEqual(result["total"], Decimal("118.00"))
        self.assertTrue(result["exact"])

    def test_one_paisa_balance_is_exact(self):
        result = find_transfer_amount("0.01", METHOD_NPSB, npsb_per_1000="8.50")
        self.assertEqual(result["transfer"], Decimal("0.01"))
        self.assertEqual(result["fee"], Decimal("0.00"))
        self.assertEqual(result["total"], Decimal("0.01"))
        self.assertTrue(result["exact"])

    def test_no_exact_match_returns_closest_safe(self):
        # 100.00: 99.16 + 0.84 = 100.00 is impossible; 99.15 + 0.84 = 99.99 is the best fit.
        result = find_transfer_amount("100.00", METHOD_NPSB, npsb_per_1000="8.50")
        self.assertLessEqual(result["total"], Decimal("100.00"))
        self.assertEqual(result["total"] + result["remaining"], Decimal("100.00"))

    def test_balance_below_bank_min_fee(self):
        result = find_transfer_amount("5.00", METHOD_BANK)
        self.assertEqual(result["transfer"], Decimal("0.00"))
        self.assertEqual(result["remaining"], Decimal("5.00"))
        self.assertFalse(result["exact"])

    def test_binary_search_matches_brute_force(self):
        cases = [
            (METHOD_NPSB, ROUND_NORMAL), (METHOD_NPSB, ROUND_CUT_DOWN),
            (METHOD_BANK, ROUND_NORMAL), (METHOD_BANK, ROUND_CUT_DOWN),
        ]
        for balance in ("0.37", "9.99", "10.13", "118", "507.37", "940.39", "1234.56", "6637.70"):
            for method, rounding in cases:
                result = find_transfer_amount(balance, method, rounding_mode=rounding)
                bal = money(balance)
                expected = Decimal("0.00")
                for cents in range(int(bal * 100), -1, -1):
                    t = Decimal(cents) / 100
                    if t + calculate_fee(t, method, rounding_mode=rounding) <= bal:
                        expected = money(t)
                        break
                self.assertEqual(result["transfer"], expected, (balance, method, rounding))

    def test_large_balance_is_fast(self):
        import time
        start = time.perf_counter()
        find_transfer_amount("9999999.99", METHOD_BANK)
        self.assertLess(time.perf_counter() - start, 0.5)

    def test_non_finite_and_huge_inputs_rejected(self):
        for value in ("nan", "inf", "-inf", "1e30", "", "   "):
            with self.assertRaises(CalculatorError):
                find_transfer_amount(value, METHOD_NPSB)

    def test_empty_input_message(self):
        with self.assertRaisesRegex(CalculatorError, "cannot be empty"):
            find_transfer_amount("", METHOD_NPSB)

    def test_commas_and_currency_accepted(self):
        self.assertEqual(money("1,000.50"), Decimal("1000.50"))
        self.assertEqual(money("৳ 940.39"), Decimal("940.39"))
        self.assertEqual(money("940.39 Tk"), Decimal("940.39"))

    def test_negative_or_invalid_fee_settings_rejected(self):
        for bad in ("-5", "nan"):
            with self.assertRaises(CalculatorError):
                find_transfer_amount("100", METHOD_NPSB, npsb_per_1000=bad)
            with self.assertRaises(CalculatorError):
                find_transfer_amount("100", METHOD_BANK, bank_percent=bad)
            with self.assertRaises(CalculatorError):
                find_transfer_amount("100", METHOD_BANK, min_fee=bad)

    def test_fee_rate_keeps_full_precision(self):
        # 1.255% of 1000 = 12.55; rounding the rate to 1.26% would give 12.60.
        self.assertEqual(calculate_fee("1000", METHOD_BANK, bank_percent="1.255"), Decimal("12.55"))

    def test_cut_down_rounding_mode(self):
        fee = calculate_fee("932.46", METHOD_NPSB, npsb_per_1000="8.50", rounding_mode=ROUND_CUT_DOWN)
        self.assertEqual(fee, Decimal("7.92"))

    def test_invalid_number(self):
        with self.assertRaises(CalculatorError):
            find_transfer_amount("abc", METHOD_NPSB)

    def test_main_returns_zero_without_system_exit_for_help(self):
        exit_code = main(["--help"])
        self.assertEqual(exit_code, 0)

    def test_main_returns_zero_without_system_exit_for_cli(self):
        exit_code = main(["--balance", "940.39", "--method", "npsb"])
        self.assertEqual(exit_code, 0)

    def test_main_returns_one_for_bad_cli_input(self):
        exit_code = main(["--balance", "abc", "--method", "npsb"])
        self.assertEqual(exit_code, 1)

    def test_parse_cli_args_never_raises_for_unknown_argument(self):
        args, error = parse_cli_args(["--unknown"])
        self.assertIsNone(args)
        self.assertEqual(error, "Unknown argument: --unknown")

    def test_parse_cli_args_missing_value(self):
        args, error = parse_cli_args(["--balance"])
        self.assertIsNone(args)
        self.assertEqual(error, "Missing value after --balance")

    def test_main_returns_zero_for_tests_without_system_exit(self):
        suite = unittest.TestSuite()
        suite.addTest(BkashCalculatorTests("test_invalid_method"))
        runner = unittest.TextTestRunner(verbosity=0)
        result = runner.run(suite)
        self.assertTrue(result.wasSuccessful())

    def test_parse_cli_args_gui_flag(self):
        args, error = parse_cli_args(["--gui"])
        self.assertIsNone(error)
        self.assertTrue(args["gui"])

    def test_help_mentions_standalone(self):
        self.assertIn("standalone desktop app", help_text().lower())

    def test_pyside6_import_is_lazy(self):
        # The calculator functions and tests must work even if PySide6 is not installed.
        self.assertEqual(calculate_fee("100", METHOD_NPSB), Decimal("0.85"))

    def test_professional_ui_text_not_in_help(self):
        self.assertIn("TakaTrim", help_text())
        self.assertIn("Double-click the EXE", help_text())


def run_tests():
    suite = unittest.defaultTestLoader.loadTestsFromTestCase(BkashCalculatorTests)
    runner = unittest.TextTestRunner(verbosity=2)
    result = runner.run(suite)
    return 0 if result.wasSuccessful() else 1


def ensure_std_streams():
    """A --noconsole EXE has no stdout/stderr; print() would crash without this."""
    if sys.stdout is None:
        sys.stdout = open(os.devnull, "w", encoding="utf-8")
    if sys.stderr is None:
        sys.stderr = open(os.devnull, "w", encoding="utf-8")


def main(argv=None):
    ensure_std_streams()
    if argv is None:
        argv = sys.argv[1:]

    args, error = parse_cli_args(argv)

    if error:
        print(f"Error: {error}", file=sys.stderr)
        print("\n" + help_text())
        return 1

    if args["test"]:
        return run_tests()

    if args["help"]:
        print(help_text())
        return 0

    # No arguments or --gui means normal double-click desktop app.
    if args["gui"] or not args["balance"]:
        return run_gui_app()

    try:
        run_cli(args)
        return 0
    except CalculatorError as exc:
        print(f"Error: {exc}", file=sys.stderr)
        return 1


if __name__ == "__main__":
    # Do not call sys.exit() or raise SystemExit here.
    # Some notebook/sandbox runners display clean SystemExit: 0 as an error.
    main()
