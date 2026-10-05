.DEFAULT_GOAL := help

# Serialize targets so formatting cannot race with validation, even with make -j.
.NOTPARALLEL:

GRADLE ?= ./gradlew
GRADLE_FLAGS ?= --no-daemon
GRADLE_USER_HOME ?= $(CURDIR)/.gradle-user-home
export GRADLE_USER_HOME

DESKTOP_GRADLE = $(GRADLE) $(GRADLE_FLAGS) -PdesktopOnly=true
DESKTOP_APP := ./desktop/build/compose/binaries/main/app/mnemolink/bin/mnemolink

.PHONY: help run test check build format image run-packaged smoke package test-ollama android-check clean

help:
	@printf '%s\n' \
		'MnemoLink development commands (desktop targets need no Android SDK):' \
		'  make run           Run the desktop app from source (requires a display)' \
		'  make test          Run core and desktop JVM tests; no live model requests' \
		'  make check         Check formatting and build/test core and desktop' \
		'  make build         Build the desktop application and its JVM dependencies' \
		'  make format        Format core and desktop Kotlin sources' \
		'  make image         Build the standalone application with bundled Java' \
		'  make run-packaged  Build and run the standalone application' \
		'  make smoke         Build image and run bounded offline Demo smoke (display required)' \
		'  make package       Build a Debian package (requires dpkg-deb and fakeroot)' \
		'  make test-ollama   Explicit live Qwen3 test with synthetic data (local model required)' \
		'  make android-check  Check Android and core (requires Android SDK)' \
		'  make clean         Remove core and desktop build outputs; keep models and Gradle cache' \
		'' \
		'Overrides: GRADLE, GRADLE_FLAGS, GRADLE_USER_HOME (also read from the environment).'

run:
	$(DESKTOP_GRADLE) :desktop:run

test:
	MNEMOLINK_OLLAMA_TEST=false $(DESKTOP_GRADLE) :core:test :desktop:test

check:
	MNEMOLINK_OLLAMA_TEST=false $(DESKTOP_GRADLE) :core:build :desktop:build

build:
	MNEMOLINK_OLLAMA_TEST=false $(DESKTOP_GRADLE) :desktop:build

format:
	$(DESKTOP_GRADLE) :core:ktlintFormat :desktop:ktlintFormat

image:
	$(DESKTOP_GRADLE) :desktop:createDistributable

run-packaged: image
	$(DESKTOP_APP)

smoke: image
	timeout 30s $(DESKTOP_APP) --smoke-test

package:
	$(DESKTOP_GRADLE) :desktop:packageDeb

test-ollama:
	MNEMOLINK_OLLAMA_TEST=true $(DESKTOP_GRADLE) :desktop:test --tests '*OllamaLocalIntegrationTest' --rerun-tasks

android-check:
	python3 tools/verify_wrapper.py
	MNEMOLINK_OLLAMA_TEST=false $(GRADLE) $(GRADLE_FLAGS) :core:build :app:ktlintCheck :app:testDebugUnitTest :app:lintDebug :app:assembleDebug :app:assembleDebugAndroidTest

clean:
	$(DESKTOP_GRADLE) :core:clean :desktop:clean
