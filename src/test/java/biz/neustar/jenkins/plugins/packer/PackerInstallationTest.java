package biz.neustar.jenkins.plugins.packer;

import hudson.Functions;
import hudson.Launcher;
import hudson.util.StreamTaskListener;
import java.io.File;
import java.util.ArrayList;
import net.sf.json.JSONObject;
import org.apache.commons.io.FileUtils;
import org.junit.Test;
import static org.junit.Assert.*;
import org.junit.Rule;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.RestartableJenkinsRule;

public class PackerInstallationTest {

    @Rule
    public RestartableJenkinsRule r = new RestartableJenkinsRule();

    @Issue("JENKINS-49715")
    @Test
    public void getExecutable() {
        r.then(new RestartableJenkinsRule.Step() {
            @Override
            public void run(JenkinsRule r) throws Throwable {
                File home = r.jenkins.getRootDir();
                File exe = new File(home, Functions.isWindows() ? PackerInstallation.WINDOWS_PACKER_COMMAND : PackerInstallation.UNIX_PACKER_COMMAND);
                FileUtils.touch(exe);
                JSONObject templateJsonObj = new JSONObject();
                templateJsonObj.put("value", TemplateMode.TEXT.toMode());
                templateJsonObj.put("jsonTemplateText", "{\"here\": \"i am\"}");
                PackerInstallation installation = new PackerInstallation("TestPacker", home.getAbsolutePath(), "params", templateJsonObj, new ArrayList<PackerFileEntry>(), null);
                Launcher launcher = r.createOnlineSlave().createLauncher(StreamTaskListener.fromStderr());
                assertEquals(exe.getAbsolutePath(), installation.getExecutable(launcher));
            }
        });
    }

    @Issue("JENKINS-49715")
    @Test
    public void installationListSaved() {
        r.then(new RestartableJenkinsRule.Step() {
            @Override
            public void run(JenkinsRule r) throws Throwable {
                JSONObject templateJsonObj = new JSONObject();
                templateJsonObj.put("value", TemplateMode.TEXT.toMode());
                templateJsonObj.put("jsonTemplateText", "{\"here\": \"i am\"}");
                PackerInstallation installation = new PackerInstallation("TestPacker", "packerHome", "params", templateJsonObj, new ArrayList<PackerFileEntry>(), null);
                r.jenkins.getDescriptorByType(PackerInstallation.DescriptorImpl.class).setInstallations(new PackerInstallation[] {installation});
            }
        });
        r.then(new RestartableJenkinsRule.Step() {
            @Override
            public void run(JenkinsRule r) throws Throwable {
                PackerInstallation[] installations = r.jenkins.getDescriptorByType(PackerInstallation.DescriptorImpl.class).getInstallations();
                assertEquals(1, installations.length);
                assertEquals("packerHome", installations[0].getHome());
            }
        });
    }

    // TODO the unusual config.jelly deserves a configRoundtrip

}
