all: submodules ruby brew gems

submodules:
	git submodule update

ruby: .ruby-version
	rbenv install -s

brew: Brewfile
	brew bundle --no-lock

gems: Gemfile Gemfile.lock
	bundle install

lint: ruby gems brew .FORCE
	bundle exec fastlane lint strict:true

start_server: submodules stop_server .FORCE
	$(SHELL) Scripts/startServer.sh

stop_server: .FORCE
	$(SHELL) Scripts/stopServer.sh

setup_emulator: submodules teardown_emulator .FORCE
	$(SHELL) Scripts/setupEmulator.sh

teardown_emulator: .FORCE
	$(SHELL) Scripts/teardownEmulator.sh

tests: submodules ruby gems brew start_server setup_emulator .FORCE
	$(SHELL) Scripts/runTests.sh
	$(MAKE) teardown_emulator
	$(MAKE) stop_server

translations:
	bundle exec fastlane translations

.FORCE:
