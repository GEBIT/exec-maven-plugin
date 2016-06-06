package org.codehaus.mojo.exec;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.Map;

import org.apache.commons.exec.CommandLine;
import org.apache.commons.exec.DefaultExecutor;
import org.apache.commons.exec.ExecuteException;
import org.apache.commons.exec.ExecuteResultHandler;
import org.apache.commons.exec.ProcessDestroyer;
import org.apache.commons.exec.PumpStreamHandler;
import org.apache.commons.exec.ShutdownHookProcessDestroyer;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.logging.Log;
import org.codehaus.plexus.util.IOUtil;

/*
 * Licensed to the Apache Software Foundation (ASF) under one or more contributor license agreements. See the NOTICE
 * file distributed with this work for additional information regarding copyright ownership. The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with the
 * License. You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0 Unless required by
 * applicable law or agreed to in writing, software distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the specific language
 * governing permissions and limitations under the License.
 */

/**
 * Reusable class that can be used to actually spawn a process. Extracted from {@link ExecMojo}.
 *
 * @since 1.5
 */
class Executor {

    Log log;

    private ProcessDestroyer processDestroyer;

    /**
     * If set to true the child process executes asynchronously and build execution continues in parallel.
     */
    private boolean async;

    /**
     * If set to true, the asynchronous child process is destroyed upon JVM shutdown. If set to false, asynchronous
     * child process continues execution after JVM shutdown. Applies only to asynchronous processes; ignored for
     * synchronous processes.
     */
    private boolean asyncDestroyOnShutdown = true;

    public Executor(Log log, boolean async, boolean asyncDestroyOnShutdown) {
        this.log = log;
        this.async = async;
        this.asyncDestroyOnShutdown = asyncDestroyOnShutdown;
    }

    protected Log getLog() {
        return log;
    }

    public void execute(File workingDirectory, File outputFile, CommandLine commandLine, Map<String, String> enviro,
            int[] successCodes) throws MojoExecutionException {
        org.apache.commons.exec.Executor exec = new DefaultExecutor();
        exec.setWorkingDirectory(workingDirectory);
        if (successCodes != null && successCodes.length > 0) {
            exec.setExitValues(successCodes);
        }

        getLog().debug("Executing command line: " + commandLine);

        try {
            int resultCode;
            if (outputFile != null) {
                if (!outputFile.getParentFile().exists() && !outputFile.getParentFile().mkdirs()) {
                    getLog().warn("Could not create non existing parent directories for log file: " + outputFile);
                }

                FileOutputStream outputStream = null;
                try {
                    outputStream = new FileOutputStream(outputFile);

                    resultCode = executeCommandLine(exec, commandLine, enviro, outputStream);
                } finally {
                    IOUtil.close(outputStream);
                }
            } else {
                resultCode = executeCommandLine(exec, commandLine, enviro, System.out, System.err);
            }

            if (isResultCodeAFailure(resultCode, successCodes)) {
                String message = "Result of " + commandLine.toString() + " execution is: '" + resultCode + "'.";
                getLog().error(message);
                throw new MojoExecutionException(message);
            }
        } catch (ExecuteException e) {
            getLog().error("Command execution failed.", e);
            throw new MojoExecutionException("Command execution failed.", e);
        } catch (IOException e) {
            getLog().error("Command execution failed.", e);
            throw new MojoExecutionException("Command execution failed.", e);
        }
    }

    static boolean isResultCodeAFailure(int result, int[] successCodes) {
        if (successCodes == null || successCodes.length == 0) {
            return result != 0;
        }
        for (int successCode : successCodes) {
            if (successCode == result) {
                return false;
            }
        }
        return true;
    }

    protected int executeCommandLine(org.apache.commons.exec.Executor exec, CommandLine commandLine,
            Map<String, String> enviro, OutputStream out, OutputStream err) throws ExecuteException, IOException {
        // note: don't use BufferedOutputStream here since it delays the outputs MEXEC-138
        PumpStreamHandler psh = new PumpStreamHandler(out, err, System.in);
        return executeCommandLine(exec, commandLine, enviro, psh);
    }

    protected int executeCommandLine(org.apache.commons.exec.Executor exec, CommandLine commandLine,
            Map<String, String> enviro, FileOutputStream outputFile) throws ExecuteException, IOException {
        BufferedOutputStream bos = new BufferedOutputStream(outputFile);
        PumpStreamHandler psh = new PumpStreamHandler(bos);
        return executeCommandLine(exec, commandLine, enviro, psh);
    }

    protected int executeCommandLine(org.apache.commons.exec.Executor exec, final CommandLine commandLine,
            Map<String, String> enviro, final PumpStreamHandler psh) throws ExecuteException, IOException {
        exec.setStreamHandler(psh);

        int result;
        try {
            psh.start();
            if (async) {
                if (asyncDestroyOnShutdown) {
                    exec.setProcessDestroyer(getProcessDestroyer());
                }

                exec.execute(commandLine, enviro, new ExecuteResultHandler() {

                    public void onProcessFailed(ExecuteException e) {
                        getLog().error("Async process failed for: " + commandLine, e);
                    }

                    public void onProcessComplete(int exitValue) {
                        getLog().info("Async process complete, exit value = " + exitValue + " for: " + commandLine);
                        try {
                            psh.stop();
                        } catch (IOException e) {
                            getLog().error("Error stopping async process stream handler for: " + commandLine, e);
                        }
                    }
                });
                result = 0;
            } else {
                result = exec.execute(commandLine, enviro);
            }
        } finally {
            if (!async) {
                psh.stop();
            }
        }
        return result;
    }

    protected ProcessDestroyer getProcessDestroyer() {
        if (processDestroyer == null) {
            processDestroyer = new ShutdownHookProcessDestroyer();
        }
        return processDestroyer;
    }
}
