/**
 * Copyright 2000-2014 NeuStar, Inc. All rights reserved.
 * NeuStar, the Neustar logo and related names and logos are registered
 * trademarks, service marks or tradenames of NeuStar, Inc. All other
 * product names, company names, marks, logos and symbols may be trademarks
 * of their respective owners.
 */

package biz.neustar.jenkins.packer;

import com.google.common.base.Charsets;
import com.google.common.io.Files;
import com.google.common.util.concurrent.MoreExecutors;
import hudson.FilePath;
import hudson.Launcher;
import hudson.Proc;
import hudson.maven.agent.AbortException;
import hudson.model.AbstractBuild;
import hudson.model.BuildListener;
import hudson.model.FreeStyleBuild;
import hudson.model.FreeStyleProject;
import hudson.model.Node;
import hudson.model.TaskListener;
import hudson.remoting.LocalChannel;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import net.sf.json.JSONObject;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.WithoutJenkins;
import org.kohsuke.stapler.StaplerRequest;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

public class PackerJenkinsPluginTest {

    private static final String PLUGIN_HOME = "/temp/packer";

    private final String name = "TestPacker";
    private final String home = "packerHome";
    private final String params = "params";

    private final String localParams = "localParams";
    private final String jsonProjectTemplate = "projectJson";
    private final List<PackerFileEntry> emptyFileEntries = new ArrayList<>();

    @Rule
    public JenkinsRule jenkins = new JenkinsRule();


    protected JSONObject createTemplateModeJson(TemplateMode mode, String value) {
        JSONObject templateJsonObj = new JSONObject();

        templateJsonObj.put("value", mode.toMode());
        if (mode.equals(TemplateMode.FILE)) {
            templateJsonObj.put("jsonTemplate", value);
        } else if (mode.equals(TemplateMode.TEXT)) {
            templateJsonObj.put("jsonTemplateText", value);
        } else if (mode.equals(TemplateMode.GLOBAL)) {
            // nothing
        }


        return templateJsonObj;
    }

    @Test
    @WithoutJenkins
    public void testFilePathUnix() throws Exception {
        FilePath path = new FilePath(new LocalChannel(MoreExecutors.sameThreadExecutor()), "D:/Program Files\\packer");
        assertFalse(PackerPublisher.isFilePathUnix(path));
    }

    @Test
    public void testPackerInstaller() throws Exception {
        final String jsonText = "{ \"here\": \"i am\"}";

        PackerInstallation installation = new PackerInstallation(name, home,
                params, createTemplateModeJson(TemplateMode.TEXT, jsonText), emptyFileEntries, null);

        PackerInstaller installer = new PackerInstaller("1");
        installer.performInstallation(installation, jenkins.createOnlineSlave(), jenkins.createTaskListener());
        PackerInstaller.DescriptorImpl desc = new PackerInstaller.DescriptorImpl();
        assertTrue(desc.isApplicable(installation.getClass()));

        assertEquals(home + "/packer", installation.getExeFile().toString());
        assertNull(installation.getExecutable(jenkins.createLocalLauncher()));


        PackerInstallation.DescriptorImpl instDesc = new PackerInstallation.DescriptorImpl();
        assertEquals(1, instDesc.getDefaultInstallers().size());
        assertTrue(instDesc.isTextTemplateChecked(null));
        assertEquals(0, instDesc.getInstallations().length);

        PackerInstallation mockInst = mock(PackerInstallation.class);
        when(mockInst.isTextTemplate()).thenReturn(false);
        when(mockInst.isFileTemplate()).thenReturn(true);
        assertFalse(instDesc.isTextTemplateChecked(mockInst));
    }

    @Test
    @WithoutJenkins
    public void testPackerFileEntry() throws Exception {
        PackerFileEntry entry = new PackerFileEntry("1", "2");
        entry.setContents("3");
        entry.setVarFileName("4");
        assertEquals("4", entry.getVarFileName());
        assertEquals("3", entry.getContents());
    }

    @Test
    @WithoutJenkins
    public void testExceptionLogging() throws Exception {
        String s = PackerPublisher.convertException(new AbortException("Template Generation / Loading Failed"));
        assertTrue(s.length() > 0);
    }

    @Test
    @WithoutJenkins
    public void testAddEmptyParamsAsArgs() throws Exception {
        assertTrue(PackerPublisher.addParamsAsArgs("").isEmpty());
        assertTrue(PackerPublisher.addParamsAsArgs(null).isEmpty());
    }


    @Test
    @WithoutJenkins
    public void testParamParsing() throws Exception {
        List<String> argList = PackerPublisher.addParamsAsArgs("-var blah=test");
        assertEquals(2, argList.toArray().length);
        assertEquals("-var", argList.toArray()[0]);
        assertEquals("blah=test", argList.toArray()[1]);

        argList = PackerPublisher.addParamsAsArgs("-var \'blah=test\'");
        assertEquals(2, argList.toArray().length);
        assertEquals("-var", argList.toArray()[0]);
        assertEquals("blah=test", argList.toArray()[1]);

        argList = PackerPublisher.addParamsAsArgs("-var \'blah=test space\'");
        assertEquals(2, argList.toArray().length);
        assertEquals("-var", argList.toArray()[0]);
        assertEquals("blah=test space", argList.toArray()[1]);

        argList = PackerPublisher.addParamsAsArgs("-var \"blah=test space\" ");
        assertEquals(2, argList.toArray().length);
        assertEquals("-var", argList.toArray()[0]);
        assertEquals("blah=test space", argList.toArray()[1]);

        argList = PackerPublisher.addParamsAsArgs("-var \"blah=%{BUILD_NAME}\" ");
        assertEquals(2, argList.toArray().length);
        assertEquals("-var", argList.toArray()[0]);
        assertEquals("blah=%{BUILD_NAME}", argList.toArray()[1]);
    }

	// Test DataBound constructor of PackerInstallation
    @Test
    @WithoutJenkins
    public void testGlobalInstallation() {
        final String templateFile = "packer.json";
		PackerInstallation installation = new PackerInstallation(name, home,
				params, createTemplateModeJson(TemplateMode.FILE, templateFile), emptyFileEntries, null);

		assertEquals(name, installation.getName());
		assertEquals(home, installation.getHome());
		assertEquals(params, installation.getParams());
		assertEquals(templateFile, installation.getJsonTemplate());
	}

    @Test
    public void testPackerGlobalChecked() {
        final String jsonText = "{ \"here\": \"i am\"}";

        PackerPublisher plugin = new PackerPublisher(name,
                jsonProjectTemplate, jsonText, PLUGIN_HOME,
                localParams, emptyFileEntries, false);
        plugin.setTemplateMode("");
        assertTrue(plugin.isGlobalTemplateChecked());

        // descriptor check marks
        PackerPublisher.DescriptorImpl desc = plugin.getDescriptor();
        assertTrue(desc.isGlobalTemplateChecked(null));

        PackerPublisher mockPacker = mock(PackerPublisher.class);
        when(mockPacker.isGlobalTemplateChecked()).thenReturn(false);
        when(mockPacker.getGlobalTemplate()).thenReturn("testing");
        assertFalse(desc.isGlobalTemplateChecked(mockPacker));
        assertEquals("testing", desc.getGlobalTemplate(mockPacker));
    }

    @Test
    @WithoutJenkins
    public void testJobPluginTextInJob() {
        final String jsonText = "{ \"here\": \"i am\"}";

        PackerPublisher plugin = new PackerPublisher(name,
                jsonProjectTemplate, jsonText, PLUGIN_HOME,
                localParams, emptyFileEntries, false);

        assertEquals(PLUGIN_HOME, plugin.getPackerHome());
        // text in job initialization
        plugin.setTemplateMode(TemplateMode.TEXT.toMode());
        assertEquals(localParams, plugin.getParams());

        assertFalse(plugin.isFileTemplate());
        assertFalse(plugin.isGlobalTemplate());

        assertTrue(plugin.isTextTemplate());
        assertEquals(jsonText, plugin.getJsonTemplateText());
    }

    @Test
    @WithoutJenkins
    public void testJobPluginFileInJob() {
        final String jsonFile = "somefile.json";

        PackerPublisher plugin = new PackerPublisher(name,
                jsonFile, "{ \"here\": \"i am\"}", PLUGIN_HOME,
                localParams, emptyFileEntries, false);

        assertEquals(PLUGIN_HOME, plugin.getPackerHome());
        // text in job initialization
        plugin.setTemplateMode(TemplateMode.FILE.toMode());
        assertEquals(localParams, plugin.getParams());

        assertFalse(plugin.isTextTemplate());
        assertFalse(plugin.isGlobalTemplate());

        assertTrue(plugin.isFileTemplate());
        assertEquals(jsonFile, plugin.getJsonTemplate());
    }

    @Test
    public void testJobPluginGlobalInJob() {
        final String jsonText = "{ \"here\": \"i am\"}";
        PackerInstallation installation = new PackerInstallation(name, home,
                params, createTemplateModeJson(TemplateMode.TEXT, jsonText), emptyFileEntries, null);

        PackerPublisher plugin = new PackerPublisher(name,
                "somefile.json", "{ \"here\": \"i am\"}", PLUGIN_HOME,
                localParams, emptyFileEntries, false);

        PackerInstallation[] installations = new PackerInstallation[1];
        installations[0] = installation;
        assertNull(plugin.getInstallation()); // should be null before
        plugin.getDescriptor().setInstallations(installations);

        assertEquals(PLUGIN_HOME, plugin.getPackerHome());
        // text in job initialization
        plugin.setTemplateMode(TemplateMode.GLOBAL.toMode());
        assertEquals(localParams, plugin.getParams());

        assertFalse(plugin.isTextTemplate());
        assertFalse(plugin.isFileTemplate());

        assertTrue(plugin.isGlobalTemplate());
        assertTrue(plugin.getGlobalTemplate().length() > 0);
    }


    @Test
    public void testPluginInJobPathExec() throws Exception {
        final String jsonText = "{ \"here\": \"i am\"}";
        PackerInstallation installation = new PackerInstallation(name, home,
                params, createTemplateModeJson(TemplateMode.TEXT, jsonText), emptyFileEntries, null);

        final String pluginHome = "bin";
        PackerPublisher placeHolder = new PackerPublisher(name,
                null, null, pluginHome, localParams, emptyFileEntries, false);

        PackerInstallation[] installations = new PackerInstallation[1];
        installations[0] = installation;

        placeHolder.getDescriptor().setInstallations(installations);

        StaplerRequest mockReq = mock(StaplerRequest.class);
        when(mockReq.bindJSON(any(Class.class), any(JSONObject.class))).thenReturn(placeHolder);

        JSONObject formJson = new JSONObject();
        formJson.put("templateMode", createTemplateModeJson(TemplateMode.TEXT, jsonText));
        PackerPublisher plugin = placeHolder.getDescriptor().newInstance(mockReq, formJson);

        assertEquals(pluginHome, plugin.getPackerHome());
        assertEquals(localParams, plugin.getParams());

        assertTrue(plugin.isTextTemplate());
        assertFalse(plugin.isFileTemplate());
        assertFalse(plugin.isGlobalTemplate());
        assertEquals(jsonText, plugin.getJsonTemplateText());

        FreeStyleProject project = jenkins.createFreeStyleProject();
        FreeStyleBuild build = project.scheduleBuild2(0).get();

        Launcher launcherMock = mock(Launcher.class);
        TaskListener listenerMock = mock(TaskListener.class);

        String exec = plugin.getRemotePackerExec(build, launcherMock, listenerMock);

        assertEquals(build.getWorkspace().getRemote() + "/" + pluginHome + "/packer", exec);
    }



    @Test
    public void testPluginInJobAbsPathExec() throws Exception {

        final String jsonText = "{ \"here\": \"i am\"}";
        PackerInstallation installation = new PackerInstallation(name, home,
                params, createTemplateModeJson(TemplateMode.TEXT, jsonText), emptyFileEntries, null);

        PackerPublisher placeHolder = new PackerPublisher(name,
                null, null, PLUGIN_HOME, localParams, emptyFileEntries, false);

        PackerInstallation[] installations = new PackerInstallation[1];
        installations[0] = installation;

        placeHolder.getDescriptor().setInstallations(installations);

        StaplerRequest mockReq = mock(StaplerRequest.class);
        when(mockReq.bindJSON(any(Class.class), any(JSONObject.class))).thenReturn(placeHolder);

        JSONObject formJson = new JSONObject();
        formJson.put("templateMode", createTemplateModeJson(TemplateMode.TEXT, jsonText));
        PackerPublisher plugin = placeHolder.getDescriptor().newInstance(mockReq, formJson);

        assertEquals(PLUGIN_HOME, plugin.getPackerHome());
        assertEquals(localParams, plugin.getParams());

        assertTrue(plugin.isTextTemplate());
        assertFalse(plugin.isFileTemplate());
        assertFalse(plugin.isGlobalTemplate());
        assertEquals(jsonText, plugin.getJsonTemplateText());

        FreeStyleProject project = jenkins.createFreeStyleProject();
        FreeStyleBuild build = project.scheduleBuild2(0).get();

        Launcher launcherMock = mock(Launcher.class);
        TaskListener listenerMock = mock(TaskListener.class);

        String exec = plugin.getRemotePackerExec(build, launcherMock, listenerMock);

        assertEquals(PLUGIN_HOME + "/packer", exec);
    }



    @Test
    public void testPluginInJobWindowsPathExec() throws Exception {
        final String jsonText = "{ \"here\": \"i am\"}";
        PackerInstallation installation = new PackerInstallation(name, home,
                params, createTemplateModeJson(TemplateMode.TEXT, jsonText), emptyFileEntries, null);

        final String pluginHome = "bin";
        PackerPublisher placeHolder = new PackerPublisher(name,
                null, null, pluginHome, localParams, emptyFileEntries, false);

        PackerInstallation[] installations = new PackerInstallation[1];
        installations[0] = installation;

        placeHolder.getDescriptor().setInstallations(installations);

        StaplerRequest mockReq = mock(StaplerRequest.class);
        when(mockReq.bindJSON(any(Class.class), any(JSONObject.class))).thenReturn(placeHolder);

        JSONObject formJson = new JSONObject();
        formJson.put("templateMode", createTemplateModeJson(TemplateMode.TEXT, jsonText));
        PackerPublisher plugin = placeHolder.getDescriptor().newInstance(mockReq, formJson);

        assertEquals(pluginHome, plugin.getPackerHome());
        assertEquals(localParams, plugin.getParams());

        assertTrue(plugin.isTextTemplate());
        assertFalse(plugin.isFileTemplate());
        assertFalse(plugin.isGlobalTemplate());
        assertEquals(jsonText, plugin.getJsonTemplateText());

        FreeStyleProject project = jenkins.createFreeStyleProject();
        FreeStyleBuild build = project.scheduleBuild2(0).get();

        FilePath winFilePath = new FilePath(new LocalChannel(MoreExecutors.sameThreadExecutor()), "C:\\");
        Node mockNode = mock(Node.class);
        when(mockNode.createPath(anyString())).thenReturn(winFilePath);

        FreeStyleBuild mockBuildWin = spy(build);
        when(mockBuildWin.getBuiltOn()).thenReturn(mockNode);

        Launcher launcherMock = mock(Launcher.class);
        TaskListener listenerMock = mock(TaskListener.class);

        String exec = plugin.getRemotePackerExec(mockBuildWin, launcherMock, listenerMock);

        // C:\bin\packer.exe
        assertEquals(mockBuildWin.getWorkspace().getRemote() + pluginHome + "\\" + PackerInstallation.WINDOWS_PACKER_COMMAND,
                exec);
    }

    @Test
    public void testPluginInJobWindowsAbsPathExec() throws Exception {
        final String jsonText = "{ \"here\": \"i am\"}";
        PackerInstallation installation = new PackerInstallation(name, home,
                params, createTemplateModeJson(TemplateMode.TEXT, jsonText), emptyFileEntries, null);

        final String pluginHome = "D:\\bin";
        PackerPublisher placeHolder = new PackerPublisher(name,
                null, null, pluginHome, localParams, emptyFileEntries, false);

        PackerInstallation[] installations = new PackerInstallation[1];
        installations[0] = installation;

        placeHolder.getDescriptor().setInstallations(installations);

        StaplerRequest mockReq = mock(StaplerRequest.class);
        when(mockReq.bindJSON(any(Class.class), any(JSONObject.class))).thenReturn(placeHolder);

        JSONObject formJson = new JSONObject();
        formJson.put("templateMode", createTemplateModeJson(TemplateMode.TEXT, jsonText));
        PackerPublisher plugin = placeHolder.getDescriptor().newInstance(mockReq, formJson);

        assertEquals(pluginHome, plugin.getPackerHome());
        assertEquals(localParams, plugin.getParams());

        assertTrue(plugin.isTextTemplate());
        assertFalse(plugin.isFileTemplate());
        assertFalse(plugin.isGlobalTemplate());
        assertEquals(jsonText, plugin.getJsonTemplateText());

        FreeStyleProject project = jenkins.createFreeStyleProject();
        FreeStyleBuild build = project.scheduleBuild2(0).get();

        FilePath winFilePath = new FilePath(new LocalChannel(MoreExecutors.sameThreadExecutor()), "C:\\");
        Node mockNode = mock(Node.class);
        when(mockNode.createPath(anyString())).thenReturn(winFilePath);

        FreeStyleBuild mockBuildWin = spy(build);
        when(mockBuildWin.getBuiltOn()).thenReturn(mockNode);

        Launcher launcherMock = mock(Launcher.class);
        TaskListener listenerMock = mock(TaskListener.class);

        String exec = plugin.getRemotePackerExec(mockBuildWin, launcherMock, listenerMock);
        // D:\bin\\packer.exe
        assertEquals(pluginHome + "\\" +  PackerInstallation.WINDOWS_PACKER_COMMAND, exec);
    }


    @Test
    public void testPluginBuild() throws Exception {
        final String jsonText = "{ \"here\": \"i am\"}";

        List<PackerFileEntry> globalFileEntries = new ArrayList<>();
        globalFileEntries.add(new PackerFileEntry("x509_cert", "cert here"));
        globalFileEntries.add(new PackerFileEntry("x509_key", "the key"));

        PackerInstallation installation = new PackerInstallation(name, home,
                "-var 'a=b'", createTemplateModeJson(TemplateMode.TEXT, jsonText), globalFileEntries, null);

        final String pluginHome = "bin";

        List<PackerFileEntry> wkspFileEntries = new ArrayList<>();
        globalFileEntries.add(new PackerFileEntry("x509_cert", "in build"));
        globalFileEntries.add(new PackerFileEntry("blah", "whatever"));
        PackerPublisher placeHolder = new PackerPublisher(name,
                null, null, pluginHome, "-var 'ami=123'", wkspFileEntries, false);

        PackerInstallation[] installations = new PackerInstallation[1];
        installations[0] = installation;

        placeHolder.getDescriptor().setInstallations(installations);

        StaplerRequest mockReq = mock(StaplerRequest.class);
        when(mockReq.bindJSON(any(Class.class), any(JSONObject.class))).thenReturn(placeHolder);

        JSONObject formJson = new JSONObject();
        formJson.put("templateMode", createTemplateModeJson(TemplateMode.TEXT, jsonText));
        formJson.put("useDebug", true);
        PackerPublisher plugin = placeHolder.getDescriptor().newInstance(mockReq, formJson);

        FreeStyleProject project = jenkins.createFreeStyleProject();
        final FreeStyleBuild build = project.scheduleBuild2(0).get();

        Launcher launcherMock = mock(Launcher.class);
        BuildListener buildListenerMock = mock(BuildListener.class);

        final Proc procMock = mock(Proc.class);
        when(procMock.join()).thenReturn(0);
        when(launcherMock.launch(any(Launcher.ProcStarter.class))).then(new Answer<Proc>() {
            public Proc answer(InvocationOnMock invocation) throws Throwable {
                Launcher.ProcStarter param = (Launcher.ProcStarter) invocation.getArguments()[0];

                List<String> cmds = param.cmds();

                // Should be something like:
                // cmds: [/var/folders/5b/fpx3w1510fg4lg7_gxrn_lpwndtvmg/T/hudson8360654264565261160test/workspace/test0/bin/packer,
                //        build, -var, a=b, -var, ami=123, -var, x509_cert=/var/folders/5b/fpx3w1510fg4lg7_gxrn_lpwndtvmg/T/x509_cert9011367320263994533.tmp,
                //        -var, x509_key=/var/folders/5b/fpx3w1510fg4lg7_gxrn_lpwndtvmg/T/x509_key1483589375485258832.tmp,
                //        -var, blah=/var/folders/5b/fpx3w1510fg4lg7_gxrn_lpwndtvmg/T/blah2646977772189210041.tmp,
                //        /var/folders/5b/fpx3w1510fg4lg7_gxrn_lpwndtvmg/T/packer8212864036611789292.json]

                assertEquals(7 + 6, cmds.size());
                assertEquals(build.getWorkspace().getRemote() + "/bin/packer", cmds.get(0));
                assertEquals("build", cmds.get(1));
                assertEquals("-var", cmds.get(2));
                assertEquals("a=b", cmds.get(3));
                assertEquals("-var", cmds.get(4));
                assertEquals("ami=123", cmds.get(5));

                String f = cmds.get(7).split("=")[1];
                assertEquals("in build", Files.toString(new File(f), Charsets.UTF_8));

                assertTrue(cmds.get(12).endsWith(".json"));
                return procMock;
            }
        });

        assertTrue(plugin.perform((AbstractBuild) build, launcherMock, buildListenerMock));
    }

}
