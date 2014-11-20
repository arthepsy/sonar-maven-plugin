/*
 * The MIT License
 *
 * Copyright 2009 The Codehaus.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of
 * this software and associated documentation files (the "Software"), to deal in
 * the Software without restriction, including without limitation the rights to
 * use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies
 * of the Software, and to permit persons to whom the Software is furnished to do
 * so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package org.codehaus.mojo.sonar;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.apache.maven.artifact.repository.ArtifactRepository;
import org.apache.maven.artifact.repository.DefaultArtifactRepository;
import org.apache.maven.artifact.repository.layout.DefaultRepositoryLayout;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.testing.MojoRule;
import org.codehaus.mojo.sonar.mock.MockHttpServerInterceptor;
import org.fest.assertions.MapAssert;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.rules.TemporaryFolder;
import org.skyscreamer.jsonassert.JSONAssert;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.Properties;

import static org.fest.assertions.Assertions.assertThat;
import static org.fest.assertions.MapAssert.entry;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class SonarMojoTest
{

    @Rule
    public ExpectedException thrown = ExpectedException.none();

    @Rule
    public TemporaryFolder temp = new TemporaryFolder();

    @Rule
    public MojoRule mojoRule = new MojoRule();

    @Rule
    public MockHttpServerInterceptor mockHttp = new MockHttpServerInterceptor();

    private SonarMojo getMojo( File baseDir )
        throws Exception
    {
        return (SonarMojo) mojoRule.lookupConfiguredMojo( baseDir, "sonar" );
    }

    @Test
    public void sonar37SupportsAnyMavenVersion()
        throws Exception
    {
        ServerMetadata serverMetadata = mock( ServerMetadata.class );
        when( serverMetadata.getVersion() ).thenReturn( "3.7" );
        when( serverMetadata.supportsMaven3() ).thenReturn( true );
        when( serverMetadata.supportsMaven3_1() ).thenReturn( true );

        SonarMojo mojo =
            getMojo( new File( "src/test/resources/org/codehaus/mojo/sonar/SonarMojoTest/sample-project" ) );
        mojo.checkVersionRequirements( serverMetadata, "3.0.5" );
        mojo.checkVersionRequirements( serverMetadata, "3.1" );
        mojo.checkVersionRequirements( serverMetadata, "4.0" );
    }

    @Test
    public void sonar36SupportsMavenVersionUpTo30()
        throws Exception
    {
        ServerMetadata serverMetadata = mock( ServerMetadata.class );
        when( serverMetadata.getVersion() ).thenReturn( "3.6" );
        when( serverMetadata.supportsMaven3() ).thenReturn( true );
        when( serverMetadata.supportsMaven3_1() ).thenReturn( false );

        SonarMojo mojo =
            getMojo( new File( "src/test/resources/org/codehaus/mojo/sonar/SonarMojoTest/sample-project" ) );
        mojo.checkVersionRequirements( serverMetadata, "3.0.5" );

        thrown.expect( MojoExecutionException.class );
        thrown.expectMessage( "SonarQube 3.6 does not support Maven 3.1" );
        mojo.checkVersionRequirements( serverMetadata, "3.1" );
    }

    @Test
    public void sonar23DoesntSupportMaven3()
        throws Exception
    {
        ServerMetadata serverMetadata = mock( ServerMetadata.class );
        when( serverMetadata.getVersion() ).thenReturn( "2.3" );
        when( serverMetadata.supportsMaven3() ).thenReturn( false );
        when( serverMetadata.supportsMaven3_1() ).thenReturn( false );

        thrown.expect( MojoExecutionException.class );
        thrown.expectMessage( "SonarQube 2.3 does not support Maven 3" );

        SonarMojo mojo =
            getMojo( new File( "src/test/resources/org/codehaus/mojo/sonar/SonarMojoTest/sample-project" ) );
        mojo.checkVersionRequirements( serverMetadata, "3.1" );

    }

    @Test
    public void executeMojo()
        throws Exception
    {

        mockHttp.setMockResponseData( "4.3" );

        final ArtifactRepository localRepository =
            new DefaultArtifactRepository( "local",
                                           temp.newFolder().toURI().toURL().toString(), new DefaultRepositoryLayout() );

        SonarMojo mojo =
            getMojo( new File( "src/test/resources/org/codehaus/mojo/sonar/SonarMojoTest/sample-project" ) );
        mojo.setLocalRepository( localRepository );
        mojo.setSonarHostURL( "http://localhost:" + mockHttp.getPort() );
        mojo.execute();

        assertPropsContains( entry( "sonar.projectKey", "org.codehaus.sonar:sample-project" ) );
    }

    @Test
    public void shouldExportBinaries()
        throws Exception
    {

        mockHttp.setMockResponseData( "4.3" );

        File localRepo = temp.newFolder();
        final ArtifactRepository localRepository =
            new DefaultArtifactRepository( "local",
                                           localRepo.toURI().toURL().toString(), new DefaultRepositoryLayout() );
        File commonsIo = new File( localRepo, "commons-io/commons-io/2.4/commons-io-2.4.jar" );
        FileUtils.forceMkdir( commonsIo.getParentFile() );
        commonsIo.createNewFile();

        File baseDir = new File( "src/test/resources/org/codehaus/mojo/sonar/SonarMojoTest/sample-project" );
        SonarMojo mojo =
            getMojo( baseDir );
        mojo.setLocalRepository( localRepository );
        mojo.setSonarHostURL( "http://localhost:" + mockHttp.getPort() );
        mojo.execute();

        assertPropsContains( entry( "sonar.binaries", new File( baseDir, "target/classes" ).getAbsolutePath() ) );
    }

    @Test
    public void shouldExportDefaultWarWebSource()
        throws Exception
    {

        mockHttp.setMockResponseData( "4.3" );

        File localRepo = temp.newFolder();
        final ArtifactRepository localRepository =
            new DefaultArtifactRepository( "local",
                                           localRepo.toURI().toURL().toString(), new DefaultRepositoryLayout() );

        File baseDir = new File( "src/test/resources/org/codehaus/mojo/sonar/SonarMojoTest/sample-war-project" );
        SonarMojo mojo =
            getMojo( baseDir );
        mojo.setLocalRepository( localRepository );
        mojo.setSonarHostURL( "http://localhost:" + mockHttp.getPort() );
        mojo.execute();

        assertPropsContains( entry( "sonar.sources", new File( baseDir, "src/main/webapp" ).getAbsolutePath() + ","
            + new File( baseDir, "src/main/java" ).getAbsolutePath() ) );
    }

    @Test
    public void shouldExportOverridenWarWebSource()
        throws Exception
    {

        mockHttp.setMockResponseData( "4.3" );

        File localRepo = temp.newFolder();
        final ArtifactRepository localRepository =
            new DefaultArtifactRepository( "local",
                                           localRepo.toURI().toURL().toString(), new DefaultRepositoryLayout() );

        File baseDir =
            new File( "src/test/resources/org/codehaus/mojo/sonar/SonarMojoTest/war-project-override-web-dir" );
        SonarMojo mojo =
            getMojo( baseDir );
        mojo.setLocalRepository( localRepository );
        mojo.setSonarHostURL( "http://localhost:" + mockHttp.getPort() );
        mojo.execute();

        assertPropsContains( entry( "sonar.sources", new File( baseDir, "web" ).getAbsolutePath() + ","
            + new File( baseDir, "src/main/java" ).getAbsolutePath() ) );
    }

    @Test
    public void shouldExportDependencies()
        throws Exception
    {

        mockHttp.setMockResponseData( "5.0" );

        File localRepo = new File( "src/test/resources/org/codehaus/mojo/sonar/SonarMojoTest/repository" );
        final ArtifactRepository localRepository =
            new DefaultArtifactRepository( "local",
                                           localRepo.toURI().toURL().toString(), new DefaultRepositoryLayout() );

        File baseDir =
            new File( "src/test/resources/org/codehaus/mojo/sonar/SonarMojoTest/export-dependencies" );
        SonarMojo mojo =
            getMojo( baseDir );
        mojo.setLocalRepository( localRepository );
        mojo.setSonarHostURL( "http://localhost:" + mockHttp.getPort() );
        mojo.execute();

        String libJson = readProps().getProperty( "sonar.maven.projectDependencies" );

        JSONAssert.assertEquals( "[{\"k\":\"commons-io:commons-io\",\"v\":\"2.4\",\"s\":\"compile\",\"d\":["
            + "{\"k\":\"commons-lang:commons-lang\",\"v\":\"2.6\",\"s\":\"compile\",\"d\":[]}"
            + "]},"
            + "{\"k\":\"junit:junit\",\"v\":\"3.8.1\",\"s\":\"test\",\"d\":[]}]",
                                 libJson, true );
    }

    private void assertPropsContains( MapAssert.Entry... entries )
        throws FileNotFoundException, IOException
    {
        assertThat( readProps() ).includes( entries );
    }

    private Properties readProps()
        throws FileNotFoundException, IOException
    {
        FileInputStream fis = null;
        try
        {
            File dump = new File( "target/dump.properties" );
            Properties props = new Properties();
            fis = new FileInputStream( dump );
            props.load( fis );
            return props;
        }
        finally
        {
            IOUtils.closeQuietly( fis );
        }
    }

}
