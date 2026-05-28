.PHONY: build test apk install lint release clean

# Version stamped into the APK manifest for `make release`. CI overrides this
# from the tag; locally, override on the command line:
#     make release VERSION=1.2.3
VERSION ?= 0.0.0-local

build:
	./gradlew build

test:
	./gradlew testDebugUnitTest

apk:
	./gradlew :app:assembleDebug

install:
	./gradlew :app:installDebug

lint:
	./gradlew :app:lintDebug

# Unsigned release APK, the same artifact CI produces. Sign with apksigner
# before installing — see "Releases" in README.md.
release:
	./gradlew :app:assembleRelease -PansVersion=$(VERSION)

clean:
	./gradlew clean
