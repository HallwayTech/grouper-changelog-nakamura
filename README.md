# Overview
A Grouper changelog consumer that sends info to SakaiOAE/nakamura via HTTP POSTs.

The Grouper changelog is a list of events in a Grouper system stored in the database. Changelog consumers periodically process batches of those events. The loader has multiple changelog consumer jobs configured to run a intervals defined by a quartz scheduler. The loader keeps track of the last changelog entry that each consumer successfully processed so they make progress through the list of changes.

This package includes 4 Grouper changelog consumers.

### org.sakaiproject.nakamura.grouper.changelog.esb.SimpleGroupEsbConsumer
Provisions and updates "Simple Groups" and their memberships in Sakai OAE.

### org.sakaiproject.nakamura.grouper.changelog.esb.CourseGroupEsbConsumer
Provisions and updates "Course Groups" and their memberships in Sakai OAE.

### org.sakaiproject.nakamura.grouper.changelog.esb.RestrictedCourseGroupEsbConsumer
Functionally equivalent to the CourseGroupEsbConsumer. It only processes group actions if the group name matches a list stored in a database table. If you want to use the RestrictedCourseGroupEsbConsumer just replace the CourseGroupEsbConsumer class configuration for the with RestrictedCourseGroupEsbConsumer. Then add the SQL statement that retrieves a list of regular expressions to match group names. (see the example configuration below).

### org.sakaiproject.nakamura.grouper.changelog.esb.CourseTitleEsbConsumer
Respond to stem updates by storing the description attribute on the sakai:group-title property.

## User Provisioning

The SimpleGroupEsbConsumer, CourseTitleEsbConsumer, and RestrictedCourseGroupEsbConsumer will try to create users in Sakai OAE if they don't already exist. The consumer will try to get the user's full name from their grouper subject attributes. These attributes are configurable (see the examples below);

## Build Prerequisites
* git
* java 1.6
* maven 2.2.1+

## Installation

Clone this repository and use maven to build the jar.

    git clone URL
    cd grouper-changelog-nakamura
    export GROUPER_HOME=/apps/grouper/
    mvn clean package

If you're not building on the same machine you'll be running the grouper loader on set $GROUPER_HOME to any directory and copy the resulting jars to $GROUPER_HOME/lib/custom/ on your grouper server.

Configure the Grouper loader to run the two jobs. Add the following to ${GROUPER_HOME}/conf/grouper-loader.properties

    ######################################
    # Sakai OAE
    ######################################

    #########################################################################################################################
    # Provision Simple Groups

    changeLog.consumer.simpleGroup.quartzCron = 0 0 * * * ?
    changeLog.consumer.simpleGroup.class = org.sakaiproject.nakamura.grouper.changelog.esb.SimpleGroupEsbConsumer

    # You may have to change this stem. 
    # If you do make sure to update nakamura.simplegroups.adhoc.stem and nakamura.simplegroups.provisioned.stem
    changeLog.consumer.simpleGroup.elfilter =  (event.name          =~ '^edu\\:apps\\:sakaioae\\:provisioned\\:groups.*$' \
                                                 || event.groupName =~ '^edu\\:apps\\:sakaioae\\:provisioned\\:groups.*$'\
                                                 || event.name      =~ '^edu\\:apps\\:sakaioae\\:adhoc\\:groups.*$' \
                                                 || event.groupName =~ '^edu\\:apps\\:sakaioae\\:adhoc\\:groups.*$' ) \
                                               && \
                                               (event.eventType eq 'GROUP_DELETE' \
                                                 || event.eventType eq 'GROUP_ADD' \
                                                 || event.eventType eq 'MEMBERSHIP_DELETE' \
                                                 || event.eventType eq 'MEMBERSHIP_ADD')

    # Map group group names to sakai psuedo group roles
    changeLog.consumer.simpleGroup.role.map                = TAs:ta, lecturers:lecturer, students:student, managers:manager
    # Identify sakai psuedo groups by their suffixes.
    changeLog.consumer.simpleGroup.pseudoGroup.suffixes    = member, manager, student, lecturer, ta
    changeLog.consumer.simpleGroup.delete.role = member
    changeLog.consumer.simpleGroup.pseudoGroup.suffixes = member, manager

    # User provisioning
    changeLog.consumer.simpleGroup.create.users = true
    # Expose these attributes to the subject resolver in sources.xml
    # They can come from LDAP or any subject resolver that returns extra attributes
    changeLog.consumer.simpleGroup.firstname.attribute = givenName
    changeLog.consumer.simpleGroup.lastname.attribute = sn
    changeLog.consumer.simpleGroup.email.attribute = email
    # If no emaila attribute is found, the email will be set to:
    # subjectId@${changeLog.consumer.simpleGroup.email.domain}
    changeLog.consumer.simpleGroup.email.domain = example.edu

    changeLog.consumer.simpleGroup.adhoc.simplegroups.stem = edu:apps:sakaioae:adhoc:simplegroups
    changeLog.consumer.simpleGroup.adhoc.coursegroups.stem = edu:apps:sakaioae:adhoc:groups
    changeLog.consumer.simpleGroup.provisioned.simplegroups.stem = edu:apps:sakaioae:provisioned:simplegroups
    changeLog.consumer.simpleGroup.provisioned.coursegroups.stem = edu:apps:sakaioae:provisioned:groups
    changeLog.consumer.simpleGroup.institutional.simplegroups.stem = inst:sis:groups
    changeLog.consumer.simpleGroup.institutional.coursegroups.stem = inst:sis:simplegroups

    changeLog.consumer.simpleGroup.url = http://localhost:8080
    changeLog.consumer.simpleGroup.username = grouper-admin
    changeLog.consumer.simpleGroup.password = grouper
    # Set to true to test.
    changeLog.consumer.simpleGroup.dryrun = false


    #########################################################################################################################
    #########################################################################################################################

    changeLog.consumer.courseGroups.quartzCron = 0 0 * * * ?
    # changeLog.consumer.courseGroups.class = org.sakaiproject.nakamura.grouper.changelog.esb.RestrictedCourseGroupEsbConsumer
    changeLog.consumer.courseGroups.class = org.sakaiproject.nakamura.grouper.changelog.esb.CourseGroupEsbConsumer

    # You may have to change this stem. 
    # If you do make sure to update nakamura.courses.adhoc.stem and nakamura.courses.provisioned.stem
    changeLog.consumer.courseGroups.elfilter = (event.name          =~ '^edu\\:apps\\:sakaioae\\:provisioned\\:courses.*$' \
                                                 || event.groupName =~ '^edu\\:apps\\:sakaioae\\:provisioned\\:courses.*$'\
                                                 || event.name      =~ '^edu\\:apps\\:sakaioae\\:adhoc\\:courses.*$' \
                                                 || event.groupName =~ '^edu\\:apps\\:sakaioae\\:adhoc\\:courses.*$' \
                                                 || event.name      =~ '^inst\\:sis\\:courses.*$' \
                                                 || event.groupName =~ '^inst\\:sis\\:courses.*$' ) \
                                               && \
                                               (event.eventType eq 'GROUP_DELETE' \
                                                 || event.eventType eq 'GROUP_ADD' \
                                                 || event.eventType eq 'MEMBERSHIP_DELETE' \
                                                 || event.eventType eq 'MEMBERSHIP_ADD')

    # When this group is deleted we delete the sakai course shell.
    changeLog.consumer.courseGroups.delete.role = student
    # Add the sakai admin account as a lecturer
    changeLog.consumer.courseGroups.add.admin.as = ta

    # Creating Sakai group names from grouper names.
    # We use a regulr expression to capture the stem and group names in a grouper name
    # Then use the captured pieces to compose a sakai groupId
    #
    # Regex indices                                                                                                    0       1       2       3       4       5       6
    changeLog.consumer.courseGroups.TemplateGroupIdAdapter.groupName.regex  = edu:apps:sakaioae:provisioned:courses:([^:]+):([^:]+):([^:]+):([^:]+):([^:]+):([^:]+):([^:]+)
    # Template to make a Sakai groupId
    changeLog.consumer.courseGroups.TemplateGroupIdAdapter.groupId.template = 'course_' + g[2] + '_' + g[3] + '_' + g[4] + '_' + g[5] + '_' + g[1] + '_' + g[6]
    # Map group group names to sakai psuedo group roles
    changeLog.consumer.courseGroups.role.map                                = TAs:ta, lecturers:lecturer, students:student, managers:manager
    # Identify sakai psuedo groups by their suffixes.
    changeLog.consumer.courseGroups.pseudoGroup.suffixes                    = member, manager, student, lecturer, ta

    # User provisioning
    changeLog.consumer.courseGroups.create.users = true
    # Expose these attributes to the subject resolver in sources.xml
    # They can come from LDAP or any subject resolver that returns extra attributes
    changeLog.consumer.courseGroups.firstname.attribute = givenName
    changeLog.consumer.courseGroups.lastname.attribute = sn
    changeLog.consumer.courseGroups.email.attribute = email
    # If no emaila attribute is found, the email will be set to:
    # subjectId@${changeLog.consumer.simpleGroup.email.domain}
    changeLog.consumer.courseGroups.email.domain = example.edu

    # Where the groups are in grouper
    changeLog.consumer.courseGroups.adhoc.simplegroups.stem = edu:apps:sakaioae:adhoc:simplegroups
    changeLog.consumer.courseGroups.adhoc.coursegroups.stem = edu:apps:sakaioae:adhoc:groups
    changeLog.consumer.courseGroups.provisioned.simplegroups.stem = edu:apps:sakaioae:provisioned:simplegroups
    changeLog.consumer.courseGroups.provisioned.coursegroups.stem = edu:apps:sakaioae:provisioned:groups
    changeLog.consumer.courseGroups.institutional.simplegroups.stem = inst:sis:groups
    changeLog.consumer.courseGroups.institutional.coursegroups.stem = inst:sis:simplegroups

    changeLog.consumer.courseGroups.url = http://localhost:8080
    changeLog.consumer.courseGroups.username = grouper-admin
    changeLog.consumer.courseGroups.password = grouper
    # Set to true to test.
    changeLog.consumer.courseGroups.dryrun = false
    
    # Required for the RestrictedCourseGroupEsbConsumer
    # SQL query for a list of db LIKE expressions
    # changeLog.consumer.courseGroups.restriction.query = SELECT RESTRICTION FROM COURSE_RESTRICTIONS WHERE SAKAIOAE = 1
    # Optional if you want to change the database connection profile
    # changeLog.consumer.courseGroups.db.profile = warehouse

    #########################################################################################################################
    #########################################################################################################################

    changeLog.consumer.courseTitles.quartzCron = 0 0 * * * ?
    changeLog.consumer.courseTitles.class = org.sakaiproject.nakamura.grouper.changelog.esb.CourseTitleEsbConsumer

    # You may have to change this stem. 
    # If you do make sure to update nakamura.courses.adhoc.stem and nakamura.courses.provisioned.stem
    changeLog.consumer.courseTitles.elfilter = (event.name          =~ '^edu\\:inst\\:sis\\:courses.*$' \
                                                   || event.groupName =~ '^edu\\:inst\\:sis\\:courses.*$') \
                                               &&  (event.eventType eq 'STEM_UPDATE' )
    
    changeLog.consumer.courseTitles.section.stem.regex  = edu:inst:sis:courses:([^:]+):([^:]+):([^:]+):([^:]+):([^:]+):([^:]+)

    # Regex indices                                                                                                    0       1       2       3       4       5       6
    changeLog.consumer.courseTitles.TemplateGroupIdAdapter.groupName.regex  = edu:apps:sakaioae:provisioned:courses:([^:]+):([^:]+):([^:]+):([^:]+):([^:]+):([^:]+):([^:]+)
    changeLog.consumer.courseTitles.TemplateGroupIdAdapter.groupId.template = 'course_' + g[2] + '_' + g[3] + '_' + g[4] + '_' + g[5] + '_' + g[1] + '_' + g[6]
    changeLog.consumer.courseTitles.role.map                                = TAs:ta, lecturers:lecturer, students:student, managers:manager
    changeLog.consumer.courseTitles.pseudoGroup.suffixes                    = member, manager, student, lecturer, ta

    changeLog.consumer.courseTitles.adhoc.simplegroups.stem = edu:apps:sakaioae:adhoc:simplegroups
    changeLog.consumer.courseTitles.adhoc.coursegroups.stem = edu:apps:sakaioae:adhoc:groups
    changeLog.consumer.courseTitles.provisioned.simplegroups.stem = edu:apps:sakaioae:provisioned:simplegroups
    changeLog.consumer.courseTitles.provisioned.coursegroups.stem = edu:apps:sakaioae:provisioned:groups
    changeLog.consumer.courseTitles.institutional.simplegroups.stem = inst:sis:groups
    changeLog.consumer.courseTitles.institutional.coursegroups.stem = inst:sis:simplegroups

    changeLog.consumer.courseTitles.url = http://localhost:8080
    changeLog.consumer.courseTitles.username = grouper-admin
    changeLog.consumer.courseTitles.password = grouper
    # Set to true to test.
    changeLog.consumer.courseTitles.dryrun = false


Run the Grouper Loader

    cd ${GROUPER_HOME}
    ./bin/gsh.sh -loader

Create a SakaiOAE user for the Grouper loader.

You can use the standard registration forms to create the user and set it's password.

Then add it to the administrators group to give it admin rights.

    curl -uadmin:ADMIN_PASSWORD -F:member=grouper-admin \
        http://localhost:8080/system/userManager/group/administrators.update.json

## Logging and Auditing

The package includes standard application logging as well as simplified auditing logging suitable for reporting.

Its recommended that you configure the grouper loader to send the Sakai OAE logging messages to their own file.
Do do this add the following to $GROUPER_HOME/conf/log4j.properties

    log4j.appender.grouper_sakai                            = org.apache.log4j.RollingFileAppender
    log4j.appender.grouper_sakai.File                       = ${grouper.home}logs/grouper_sakai.log
    log4j.appender.grouper_sakai.MaxFileSize                = 10000KB
    log4j.appender.grouper_sakai.MaxBackupIndex             = 1
    log4j.appender.grouper_sakai.layout                     = org.apache.log4j.PatternLayout
    log4j.appender.grouper_sakai.layout.ConversionPattern   = %d{ISO8601}: [%t] %-5p %C{1}.%M(%L) - %x - %m%n
    log4j.logger.org.sakaiproject.nakamura.grouper = INFO, grouper_sakai

Auditing logging is configured similarly in $GROUPER_HOME/conf/log4j.properties

    log4j.appender.sakai_audit                            = org.apache.log4j.DailyRollingFileAppender
    log4j.appender.sakai_audit.DatePattern                =’-'yyyy-MM-dd’.log’
    log4j.appender.sakai_audit.File                       = ${grouper.home}logs/grouper_audit
    log4j.appender.sakai_audit.layout                     = org.apache.log4j.PatternLayout
    log4j.appender.sakai_audit.layout.ConversionPattern   = %d %m%n
    log4j.logger.org.sakaiproject.nakamura.grouper.changelog.log.audit = INFO, sakai_audit

## Links
https://spaces.internet2.edu/display/Grouper/Notifications+(change+log)
http://groups.google.com/group/grouper-users/browse_thread/thread/e1cd180b39583acb?pli=1
