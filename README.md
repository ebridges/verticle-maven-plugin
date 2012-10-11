# verticle-archive

This is a Maven mojo that you can configure into the pom.xml of a vert.x project and will automate the creation of vert.x modules.

## Usage

`mvn install`

`mvn com.eqbridges.vertx:verticle-archive:1.0-SNAPSHOT:verticle`

## To Do

* Expand to include all `mod.json` parameters, remove hardcoding.
* Clean up command line parameters
* Add option to zip up module folder for easier release/deploy.
* Add capability to create a resource module, shared by other modules.


