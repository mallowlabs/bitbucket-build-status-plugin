# Bitbucket Build Status Plugin for Jenkins

A plugin for Jenkins that update Bitbucket build statuses ( https://github.com/mallowlabs/bitbucket-build-status-plugin/ ).

## Requirements

* Jenkins 2.289.1+
* Git Plugin 4.11.5+

## Author

* @mallowlabs

## How to build

* Java 11+
* Maven 3.8.1+

You need to prepare Jenkins plugins development environment.
See [Plugin Tutorial](https://wiki.jenkins-ci.org/display/JENKINS/Plugin+tutorial).

After preparation, type below command:

```shell
$ mvn package
```

You will get target/*.hpi .

