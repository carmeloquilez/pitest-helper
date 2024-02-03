# pitest-helper

![Build](https://github.com/carmeloquilez/pitest-helper/actions/workflows/build.yml/badge.svg)
![Version](https://img.shields.io/jetbrains/plugin/v/23649-pitest-helper)
![Rating](https://img.shields.io/jetbrains/plugin/r/rating/23649-pitest-helper)
![Downloads](https://img.shields.io/jetbrains/plugin/d/23649-pitest-helper)

## PITest Helper

<!-- Plugin description -->
Plugin to run PITest in your Java classes/packages using the [pitest-maven](https://pitest.org/quickstart/maven/) plugin for Maven.
The plugin builds and runs PITest mutation coverage commands for you.

Requisites:  
- A Maven project.
- pitest-maven plugin configured on your project.

Usage:  
- Right-click on your classes and packages you want to run mutation coverage and click _Run Mutation Coverage..._

Note: this plugin does not configure the pitest-maven plugin. You need to set up it beforehand.

Are you experiencing problems with the plugin? Do you have any suggestion? You can create an issue at the [Plugin Site](https://github.com/carmeloquilez/pitest-helper/issues)

PITest logo used in this plugin was created by Ling Yeung.

<!-- Plugin description end -->

## Installation

- Using the IDE built-in plugin system:
  
  <kbd>Settings/Preferences</kbd> > <kbd>Plugins</kbd> > <kbd>Marketplace</kbd> > <kbd>Search for "PITest Helper"</kbd> >
  <kbd>Install Plugin</kbd>
  
- Manually:

  Download the [latest release](https://github.com/carmeloquilez/pitest-helper/releases/latest) and install it manually using
  <kbd>Settings/Preferences</kbd> > <kbd>Plugins</kbd> > <kbd>⚙️</kbd> > <kbd>Install plugin from disk...</kbd>


---
Plugin based on the [IntelliJ Platform Plugin Template][template].

[template]: https://github.com/JetBrains/intellij-platform-plugin-template
[docs:plugin-description]: https://plugins.jetbrains.com/docs/intellij/plugin-user-experience.html#plugin-description-and-presentation
