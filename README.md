Bitbucket Build Status Plugin for Jenkins
==============================
A plugin for Jenkins that update Bitbucket build statuses ( https://github.com/mallowlabs/bitbucket-build-status-plugin/ ).

Requirements
------------------------------
* Jenkins 1.625.2+
* Credentials Plugin 1.24+
* Git Plugin 2.0+

Author
------------------------------
* @mallowlabs

How to build
------------------------------
You need to prepare Jenkins plugins development environment.
See [Plugin Tutorial](https://wiki.jenkins-ci.org/display/JENKINS/Plugin+tutorial).

After preparation, type below command:

    $ mvn package

You will get target/*.hpi .

