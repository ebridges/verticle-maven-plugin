# verticle-archive

This is a Maven mojo that can be configured into the pom.xml of a vert.x project in order to automate the assembly of vert.x modules.

## Usage

`mvn install`

`mvn verticle:verticle`

## Notes

* Dependency resolution is based on the dependency declarations in the pom.xml of the project. This mojo will copy into the verticle any dependency whose `scope` is equal to either `runtime` or `compile` (which is the default scope for a dependency).

## To Do

* Expand to include all `mod.json` parameters, remove hardcoding.
* -Fix dependency resolution: currently pulls in all dependencies.-
* -Clean up command line parameters-
* Add option to zip up module folder for easier release/deploy.
* Add capability to create a resource module, shared by other modules.


