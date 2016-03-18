# Jenkins publishing plugin for [Packer](http://packer.io)

[![Build Status](https://jenkins.ci.cloudbees.com/buildStatus/icon?job=plugins/packer-plugin)](https://jenkins.ci.cloudbees.com/job/plugins/job/packer-plugin/)


## System Configuration

Supports a template for install that can than be shared or overwritten by jobs utilizing the plugin.

## Job Configuration

Jobs can specify their own template for packer or just use a system configured one.



See the plugin wiki for more details: [Packer Plugin Wiki](https://wiki.jenkins-ci.org/display/JENKINS/Packer+Plugin)


Jenkins generates the list of installers from the [Crawler Entry](https://github.com/jenkinsci/backend-crawler/blob/master/packer.groovy)


---

Thanks goes to [NeuStar](http://neustar.biz) for sponsoring the initial work.
