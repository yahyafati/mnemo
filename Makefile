# Install Mnemo on the attached phone and/or on this computer.
#
#   make install-phone                  release APK on the attached phone (signed with your keystore)
#   make install-phone VARIANT=debug    debug APK instead
#   make install-pc                     release app image on this computer (macOS, Linux)
#   make install-all                    both
#   make help
#
# The release build is signed from MNEMO_KEYSTORE_* in the environment or local.properties
# (docs/release/signing.md); without them Gradle builds an unsigned APK that a phone refuses.
# With several devices attached, pick one: make install-phone ANDROID_SERIAL=<id> (see `adb devices`).

VARIANT ?= release
GRADLE  := ./gradlew
OS      := $(shell uname -s)

# `installDebug` / `installRelease`
ANDROID_TASK := :app:install$(shell echo $(VARIANT) | awk '{print toupper(substr($$0,1,1)) substr($$0,2)}')

DESKTOP_IMAGE := desktop/build/compose/binaries/main/app

# macOS: /Applications/Mnemo.app. Linux: ~/.local/opt/mnemo with a launcher in ~/.local/bin.
MAC_APP_DIR   ?= /Applications
LINUX_OPT_DIR ?= $(HOME)/.local/opt/mnemo
LINUX_BIN_DIR ?= $(HOME)/.local/bin

.PHONY: help install-phone install-pc install-all uninstall-phone check-phone

help:
	@echo "make install-phone [VARIANT=release|debug]   build and install the APK on the attached phone (default: release)"
	@echo "make install-pc                              build the desktop app and install it on this computer"
	@echo "make install-all                             both"
	@echo "ANDROID_SERIAL=<id> picks the phone when several are attached"

check-phone:
	@command -v adb >/dev/null 2>&1 || { echo "adb not found: add Android's platform-tools to PATH"; exit 1; }
	@n=$$(adb devices | awk 'NR>1 && $$2=="device"' | wc -l | tr -d ' '); \
	if [ "$$n" = "0" ]; then echo "No phone attached (check USB debugging and 'adb devices')"; exit 1; fi; \
	if [ "$$n" != "1" ] && [ -z "$$ANDROID_SERIAL" ]; then \
	  echo "$$n devices attached: set ANDROID_SERIAL=<id>"; adb devices; exit 1; fi

install-phone: check-phone
	$(GRADLE) $(ANDROID_TASK)
	@pkg=com.yahyafati.mnemo; \
	adb shell monkey -p $$pkg -c android.intent.category.LAUNCHER 1 >/dev/null 2>&1 || true
	@echo "Installed $(VARIANT) build on the phone."

install-pc:
	$(GRADLE) :desktop:createDistributable
ifeq ($(OS),Darwin)
	@test -d "$(DESKTOP_IMAGE)/Mnemo.app" || { echo "$(DESKTOP_IMAGE)/Mnemo.app was not built"; exit 1; }
	@-osascript -e 'tell application "Mnemo" to quit' >/dev/null 2>&1; sleep 1
	rm -rf "$(MAC_APP_DIR)/Mnemo.app"
	cp -R "$(DESKTOP_IMAGE)/Mnemo.app" "$(MAC_APP_DIR)/Mnemo.app"
	@echo "Installed $(MAC_APP_DIR)/Mnemo.app (unsigned: on first start, right-click > Open)."
else ifeq ($(OS),Linux)
	@test -x "$(DESKTOP_IMAGE)/Mnemo/bin/Mnemo" || { echo "$(DESKTOP_IMAGE)/Mnemo/bin/Mnemo was not built"; exit 1; }
	rm -rf "$(LINUX_OPT_DIR)"
	mkdir -p "$(LINUX_OPT_DIR)" "$(LINUX_BIN_DIR)"
	cp -R "$(DESKTOP_IMAGE)/Mnemo/." "$(LINUX_OPT_DIR)/"
	ln -sf "$(LINUX_OPT_DIR)/bin/Mnemo" "$(LINUX_BIN_DIR)/mnemo"
	@echo "Installed in $(LINUX_OPT_DIR); run 'mnemo' (make sure $(LINUX_BIN_DIR) is on PATH)."
else
	@echo "install-pc supports macOS and Linux. On Windows run: gradlew :desktop:packageMsi, then the .msi in desktop/build/compose/binaries/main/msi"; exit 1
endif

install-all: install-phone install-pc

# A phone that has the app signed with another key (say the debug build) refuses an update
# (INSTALL_FAILED_UPDATE_INCOMPATIBLE). Removing it deletes the app's data on the phone: back up first.
uninstall-phone: check-phone
	adb uninstall com.yahyafati.mnemo
