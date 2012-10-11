package com.eqbridges.vertx;

/*
 * Copyright 2001-2005 The Apache Software Foundation.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import static java.lang.String.format;
import static org.apache.commons.io.FileUtils.copyFileToDirectory;
import static org.apache.commons.io.IOUtils.closeQuietly;
import static org.apache.commons.lang.StringUtils.isEmpty;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.Writer;
import java.util.List;

import org.apache.maven.artifact.Artifact;
import org.apache.maven.model.Resource;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.Component;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.apache.maven.project.MavenProject;

/**
 * Mojo which assembles a verticle module.
 *
 */
@Mojo(name = "verticle", defaultPhase = LifecyclePhase.PACKAGE, threadSafe = true,
        requiresDependencyResolution = ResolutionScope.RUNTIME)
public class VerticleModuleMojo extends AbstractMojo {
    private static final String MOD_JSON = "mod.json";
    private static final String MOD_LIB = "lib";

    /**
     * Location of the assembled module.
     */
    @Parameter( defaultValue = "${project.build.directory}", required = true )
    private File outputDirectory;

    /**
     * The name of the generated verticle.
     */
    @Parameter( defaultValue = "${project.groupId}.${project.artifactId}-v${project.version}", required = true )
    private String verticleName;

    /**
     * The name of the main class for this verticle module.
     *
     */
    @Parameter( defaultValue = "${project.artifactId}", required=true)
    private String verticleMain;

    /**
  	* The Maven project.
 	*/
    @Component
    private MavenProject project;

    public void execute() throws MojoExecutionException {
        File verticleFolder = new File(outputDirectory, verticleName);

        if (!verticleFolder.exists()) {
            if(!verticleFolder.mkdirs()) {
                throw new MojoExecutionException("unable to create verticleFolder: "+ verticleFolder);
            }
        }

        createModJson(verticleFolder);

        //noinspection unchecked
        copyResources(project.getResources(), verticleFolder);

        //noinspection unchecked
        copyDependencies(project.getRuntimeArtifacts(), verticleFolder);
    }


    private void copyDependencies(List<Artifact> dependencies, File verticleFolder) throws MojoExecutionException {
        File verticleLibFolder = new File(verticleFolder, MOD_LIB);
        if(!verticleLibFolder.exists()) {
            if(!verticleLibFolder.mkdirs()) {
                throw new MojoExecutionException("unable to create verticleLibFolder: " + verticleLibFolder);
            }
        }
        for(Artifact dependency : dependencies) {
            if(null != dependency && null != dependency.getFile()) {
                File dependencyFile = dependency.getFile();
                try {
                    getLog().info(format("Copying dependency: [%s] -> [%s]", dependencyFile, verticleLibFolder));
                    copyFileToDirectory(dependencyFile, verticleLibFolder);
                } catch (IOException e) {
                    throw new MojoExecutionException("unable to copy dependency ("+dependencyFile+") to ("+verticleLibFolder+"): " + e.getMessage());
                }
            }
        }
    }

    private void copyResources(List<Resource> resources, File verticleFolder) throws MojoExecutionException {
        for(Resource resource : resources) {
            if(null != resource && !isEmpty(resource.getTargetPath())) {
                File resourceFile = new File(resource.getTargetPath());
                try {
                    getLog().info(format("Copying resource: [%s] -> [%s]", resourceFile, "/"+verticleName+"/"+MOD_LIB));
                    copyFileToDirectory(resourceFile, verticleFolder);
                } catch (IOException e) {
                    throw new MojoExecutionException("unable to copy resource ("+resourceFile+") to ("+verticleFolder+"): " + e.getMessage());
                }
            }
        }
    }

    private void createModJson(File verticleFolder) throws MojoExecutionException {
        File modJson = new File(verticleFolder,MOD_JSON);
        Writer w=null;
        try {
            w = new BufferedWriter(new FileWriter(modJson));
            w.write(modJson());
            w.flush();
        } catch (IOException e) {
            throw new MojoExecutionException("unable to create module descriptor ("+modJson+"): " + e.getMessage());
        } finally {
            if(null != w) {
                closeQuietly(w);
            }
        }
    }

    private String modJson() {
        String json = "{\n\t\"main\": \"%s\"\n}\n";
        return format(json, verticleMain);
    }
}
