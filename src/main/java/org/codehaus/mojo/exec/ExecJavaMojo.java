package org.codehaus.mojo.exec;

import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;

/**
 * Executes the supplied java class in the current VM with the enclosing project's dependencies as classpath.
 * 
 * @author <a href="mailto:kaare.nilsen@gmail.com">Kaare Nilsen</a>, <a href="mailto:dsmiley@mitre.org">David Smiley</a>
 * @since 1.0
 */
@Mojo( name = "java", threadSafe = true, requiresDependencyResolution = ResolutionScope.TEST )
public class ExecJavaMojo
    extends AbstractExecJavaMojo
{
    /**
     * The main class to execute.
     * 
     * @since 1.0
     */
    @Parameter( required = true, property = "exec.mainClass" )
    private String mainClass;

    /**
     * The class arguments.
     * 
     * @since 1.0
     */
    @Parameter( property = "exec.arguments" )
    private String[] arguments;

    @Override
    protected String[] getArguments() {
        if (this.arguments == null) {
            return new String[0];
        }
        return this.arguments;
    }

    @Override
    protected void setArguments(String[] arguments) {
        this.arguments = arguments;
    }

    @Override
    protected String getMainClass() {
        return this.mainClass;
    }
}
