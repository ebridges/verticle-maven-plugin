# verticle-maven-plugin

This is a Maven mojo that can be configured into the pom.xml of a vert.x project in order to automate the assembly of vert.x modules.

## Usage

### Installation
After downloading the most recent version (e.g. `${EXAMPLE_VERSION}` below) of the jar file, you can install it in your local environment as follows:

```bash
$ mvn install:install-file \
    -Dfile=verticle-maven-plugin-${EXAMPLE_VERSION}.jar \
    -DgroupId=com.eqbridges.vertx \
    -DartifactId=verticle-maven-plugin \
    -Dversion=${EXAMPLE_VERSION} \
    -Dpackaging=maven-plugin \
    -DgeneratePom=true \
    -DcreateChecksum=true \
```

### Configuration
After it has been successfully installed locally, you'll need to configure it in your vert.x project's POM file.  Here's an example configuration that shows all configuration options with their defaults. All options as described in the module manual are supported.  [Visit that documentation](1) for explanations of their usage.  None of the options are required.

```xml
<project>
...
  <build>
  ...
    <pluginManagement>
      <plugins>
      ...
        <plugin>
          <groupId>com.eqbridges.vertx</groupId>
          <artifactId>verticle-maven-plugin</artifactId>
          <version>${EXAMPLE_VERSION}</version>
          <executions>
            <execution>
              <goals>
                <goal>verticle</goal>
              </goals>
            </execution>
          </executions>
          <configuration>
              <verticleMain>com.example.VertxMain</verticleMain>
              <worker>false</worker>
              <preserveCwd>false</preserveCwd>
              <autoRedeploy>false</autoRedeploy>
              <includes>com.example.another-project-v0.0</includes>
          </configuration>
        </plugin>
      ...
      </plugins>
    </pluginManagement>
    ...
</project>
```

### Execution
Once your module's POM has been correctly configured, you assemble the verticle by typing:

```bash
$ mvn verticle:assemble
```

To capture and/or filter resources, enter:

```bash
$ mvn process-resources verticle:assemble
```

## Notes

* Dependency resolution is based on the dependency declarations in the pom.xml of the project. This mojo will copy into the verticle any dependency whose `scope` is equal to either `runtime` or `compile` (which is the default scope for a dependency).  If a dependency is going to be available in the runtime environment (e.g. the vertx dependencies) it should be marked with a scope of `provided`.

* The module copies the contents of the output directory (`${project.build.outputDirectory}` -- typically `target/classes`) into the root of the verticle.  This will capture any resources as well as any .class files and pull them into the verticle.  If resources need to be included and/or processed, then this should be run after the `mvn package` lifecycle phase.

* If any scripts (i.e. non-Java) are part of the verticle, this script expects to find them under `src/main/scripts`.  Review the pom.xml documentation for ways to override, augment, or otherwise configure that.

## To Do

* <del>Expand to include all `mod.json` parameters, remove hardcoding.</del>
* <del>Fix dependency resolution: currently pulls in all dependencies.</del>
* <del>Clean up command line parameters</del>
* Add option to zip up module folder for easier release/deploy.
* Add capability to create a resource module, shared by other modules.

## More Information

* [vert.x Modules Manual](1)
* [Maven MOJO API Spec](2)
* [Installing Maven Plugins](3)

## Feedback

Questions, comments, issues are always welcome.  Thanks.

[1]: http://vertx.io/mods_manual.html
[2]: http://maven.apache.org/developers/mojo-api-specification.html
[3]: http://maven.apache.org/plugins/maven-install-plugin/usage.html