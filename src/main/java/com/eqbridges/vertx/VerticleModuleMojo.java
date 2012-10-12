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
import static java.nio.file.Files.isDirectory;
import static java.util.Objects.requireNonNull;
import static org.apache.commons.io.FileUtils.copyFileToDirectory;
import static org.apache.commons.io.IOUtils.closeQuietly;
import static org.apache.commons.lang.StringUtils.isEmpty;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.Writer;
import java.nio.file.FileVisitOption;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Collection;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import org.apache.maven.artifact.Artifact;
import org.apache.maven.model.Resource;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.logging.Log;
import org.apache.maven.project.MavenProject;
import org.vertx.java.core.json.JsonObject;

/**
 * Mojo which assembles a verticle module.
 *
 * @goal assemble
 * @phase=package
 * @requiresDependencyResolution compile+runtime
 * @threadSafe
 */
@SuppressWarnings("UnusedDeclaration")
public class VerticleModuleMojo extends AbstractMojo {
    private static final String MOD_JSON = "mod.json";
    private static final String MOD_LIB = "lib";

    /**
     * Location of the assembled module.
     *
     * @parameter expression="${project.build.directory}"
     * @required
     */
    @SuppressWarnings("UnusedDeclaration")
    private File outputDirectory;

    /**
     * The name of the generated verticle.
     *
     * @parameter default-value="${project.groupId}.${project.artifactId}-v${project.version}"
     *            expression="${verticleName}"
     * @required
     */
    @SuppressWarnings("UnusedDeclaration")
    private String verticleName;

    /**
     * Runnable modules must contain a field main which specifies the main verticle to start the module. Main would be something like myscript.groovy, app.rb, foo.js or org.acme.MyApp.
     * Non-runnable modules do not need a main field.
     *
     * @parameter default-value="" expression="${verticleMain}"
     */
    @SuppressWarnings("UnusedDeclaration")
    private String verticleMain;

    /**
     * If your main verticle is a worker verticle you must also specify the worker field with value true, otherwise it will be assumed it is not a worker.
     *
     * @parameter default-value="false" expression="${worker}"
     */
    @SuppressWarnings("UnusedDeclaration")
    private Boolean worker;

    /**
     * By default when your module is executing it will see its working directory as the module directory when using the Vert.x API. This is useful if you want to package up an application which contains its own static resources into a module.
     * However in some cases, you don't want this behaviour, you want the module to see its working directory as the working directory of whoever started the module.
     * If you want to preserve current working directory, set the field preserve-cwd to true in mod.json. The default value is false.
     *
     * @parameter default-value=false expression="${preserveCwd}"
     */
    @SuppressWarnings("UnusedDeclaration")
    private Boolean preserveCwd;

    /**
     * You can configure a module to be auto-redeployed if it detects any files were modified, added or deleted in its module directory.
     * To enable auto re-deploy for a module you should specify a field auto-redeploy with a value of true in mod.json. The default value for auto-redeploy is false.
     *
     * @parameter default-value=false expression="${autoRedeploy}"
     */
    @SuppressWarnings("UnusedDeclaration")
    private Boolean autoRedeploy;

    /**
     * Sometimes you might find that different modules are using the same or similar sets of resources, e.g. .jar files or scripts, or other resources.
     * Instead of including the same resources in every module that needs them, you can put those resources in a module of their own, and then declare that other modules includes them.
     * The resources of the module will then effectively be put on the module path of the using module.
     * This is done by specifying an includes field in the module descriptor.
     *
     * @parameter default-value=false expression="${includes}"
     */
    @SuppressWarnings("UnusedDeclaration")
    private String includes;

    /**
     * The Maven project.
     * @parameter default-value="${project}"
     * @readonly
     * @required
     */
    @SuppressWarnings("UnusedDeclaration")
    private MavenProject project;

    public void execute() throws MojoExecutionException {
        File verticleFolder = new File(outputDirectory, verticleName);

        if (!verticleFolder.exists()) {
            if(!verticleFolder.mkdirs()) {
                throw new MojoExecutionException("unable to create verticleFolder: "+ verticleFolder);
            }
        }

        createModJson(verticleFolder);

        for(Object scriptSourceDir : project.getScriptSourceRoots()) {
            if(null != scriptSourceDir && !scriptSourceDir.toString().isEmpty()) {
                File scriptsDir = new File(scriptSourceDir.toString());
                if(scriptsDir.exists()) {
                    copyFiles(scriptsDir, verticleFolder);
                }
            }
        }

        String classesFolder = project.getModel().getBuild().getOutputDirectory();
        File classesDir = new File(classesFolder);
        if(classesDir.exists()) {
            copyFiles(classesDir, verticleFolder);
        }

        //noinspection unchecked
        Set<Artifact> artifacts = project.getDependencyArtifacts();
        copyDependencies(artifacts, verticleFolder);
    }

    private void copyFiles(File fromDir, File verticleFolder) throws MojoExecutionException {
        Path from = Paths.get(fromDir.toURI());
        Path to = Paths.get(verticleFolder.toURI());
        try {
            Files.walkFileTree(from, EnumSet.of(FileVisitOption.FOLLOW_LINKS),Integer.MAX_VALUE,new CopyDirVisitor(from, to, getLog()));
        } catch (IOException e) {
            throw new MojoExecutionException("unable to copy classes to verticleFolder", e);
        }
    }

    private void copyDependencies(Collection<Artifact> dependencies, File verticleFolder) throws MojoExecutionException {
        if(null != dependencies && !dependencies.isEmpty()) {
            File verticleLibFolder = new File(verticleFolder, MOD_LIB);
            if(!verticleLibFolder.exists()) {
                if(!verticleLibFolder.mkdirs()) {
                    throw new MojoExecutionException("unable to create verticleLibFolder: " + verticleLibFolder);
                }
            }

            for(Artifact dependency : dependencies) {
                if(null != dependency && null != dependency.getFile()) {
                    if(confirmDependencyScope(dependency.getScope())) {
                        File dependencyFile = dependency.getFile();
                        try {
                            getLog().info(format("Copying dependency: [%s][%s] -> [%s]", dependencyFile.getName(), dependency.getScope(), "/"+verticleName+"/"+MOD_LIB));
                            copyFileToDirectory(dependencyFile, verticleLibFolder);
                        } catch (IOException e) {
                            throw new MojoExecutionException("unable to copy dependency ("+dependencyFile+") to ("+verticleLibFolder+"): " + e.getMessage());
                        }
                    }
                }
            }
        }
    }

    private boolean confirmDependencyScope(String scope) {
        if(null != scope) {
            if(!scope.isEmpty()) {
                return scope.equals("runtime") || scope.equals("compile");
            }
        }
        return false;
    }

    private void copyResources(Collection<Resource> resources, File verticleFolder) throws MojoExecutionException {
        for(Resource resource : resources) {
            if(null != resource && !isEmpty(resource.getTargetPath())) {
                File resourceFile = new File(resource.getTargetPath());
                try {
                    getLog().info(format("Copying resource: [%s] -> [%s]", resourceFile.getName(), "/"+verticleName));
                    copyFileToDirectory(resourceFile, verticleFolder);
                } catch (IOException e) {
                    throw new MojoExecutionException("unable to copy resource ("+resourceFile+") to ("+verticleFolder+"): " + e.getMessage());
                }
            }
        }
    }

    private void createModJson(File verticleFolder) throws MojoExecutionException {
        String json = modJson();
        File modJson = new File(verticleFolder,MOD_JSON);
        Writer w=null;
        try {
            w = new BufferedWriter(new FileWriter(modJson));
            w.write(json);
            w.flush();
            getLog().info(format("Configuration written to: [%s]", modJson.getAbsolutePath()));
        } catch (IOException e) {
            throw new MojoExecutionException("unable to create module descriptor ("+modJson+"): " + e.getMessage());
        } finally {
            closeQuietly(w);
        }
    }

    private String modJson() {
        Map<String, Object> params = new HashMap<String, Object>();

        if(null != verticleMain && !verticleMain.isEmpty()) {
            getLog().info(format("Config param [%s]:[%s]", "main", verticleMain));
            params.put("main", verticleMain);
        }

        if(null != worker && worker) {
            getLog().info(format("Config param [%s]:[%s]", "worker", worker));
            params.put("worker", worker);
        }

        if(null != preserveCwd && preserveCwd) {
            getLog().info(format("Config param [%s]:[%s]", "preserve-cwd", preserveCwd));
            params.put("preserve-cwd", preserveCwd);
        }

        if(null != autoRedeploy && autoRedeploy) {
            getLog().info(format("Config param [%s]:[%s]", "auto-redeploy", autoRedeploy));
            params.put("auto-redeploy", autoRedeploy);
        }

        if(null != includes && !includes.isEmpty()) {
            getLog().info(format("Config param [%s]:[%s]", "includes", includes));
            params.put("includes", includes);
        }

        return new JsonObject(params).toString();
    }
}

class CopyDirVisitor extends SimpleFileVisitor<Path> {
    private Path fromPath;
    private Path toPath;
    private static final StandardCopyOption copyOption = StandardCopyOption.REPLACE_EXISTING;
    private final Log log;

    CopyDirVisitor(Path fromPath, Path toPath, Log log) {
        validate(fromPath);
        this.fromPath = fromPath;
        this.toPath = toPath;
        this.log = log;
    }

    @Override
    public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
        Path targetPath = toPath.resolve(fromPath.relativize(dir));
        if(!Files.exists(targetPath)){
            Files.createDirectory(targetPath);
        }
        return FileVisitResult.CONTINUE;
    }

    @Override
    public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
        Path to = toPath.resolve(fromPath.relativize(file));
        Files.copy(file, to, copyOption);
        log.info(format("Copied file [%s] -> [%s]", file.getFileName(), to.normalize()));
        return FileVisitResult.CONTINUE;
    }

    private static void validate(Path... paths) {
        for (Path path : paths) {
            requireNonNull(path);
            if (!isDirectory(path)) {
                throw new IllegalArgumentException(String.format("%s is not a directory", path.toString()));
            }
        }
    }
}
