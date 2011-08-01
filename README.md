## Overview
A Grouper changelog consumer that sends info to SakaiOAE/nakamura via HTTP POSTs.

The Grouper changelog is a list of events in a Grouper system stored in a database table/views.
The Grouper loader can have multiple changelog consumer jobs configured to run a intervals
defined by a quartz scheduler. The Grouper loader keeps track of the last changelog entry
that each consumer successfully processed.

## Build Prerequisites
* git
* java 1.6
* maven 2.2.1+

## Installation

Clone this repository and use maven to build the jar.

    git clone URL
    cd grouper-changelog-nakamura
    mvn clean install
    mvn package -Dgrouper.custom.directory=/path/to/grouper/lib/custom

If you're not building on the same machine you'll be running the grouper loader on set grouper.custom.directory to any directory and copy the resulting jars to $GROUPER_HOME/lib/custom/

Configure the Grouper loader to run the two jobs. Add the following to ${GROUPER_HOME}/conf/grouper-loader.properties

    ######################################
    # Sakai OAE
    ######################################

    # Regular expressions to determins what kind of group we're dealing with.
    nakamura.simplegroups.regex = .*simplegroups.*
    nakamura.coursegroups.regex = .*:groups:.*
    nakamura.contactgroups.regex = edu:apps:sakaioae:users:.*

	#########################################################################################################################
	# Provision Course Groups

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

    # Required for org.sakaiproject.nakamura.grouper.changelog.esb.SimpleGroupEsbConsumer
    changeLog.consumer.simpleGroup.adhoc.stem = edu:apps:sakaioae:adhoc:groups
    changeLog.consumer.simpleGroup.provisioned.stem = edu:apps:sakaioae:provisioned:groups
	changeLog.consumer.simpleGroup.createDeleteRole = member
	changeLog.consumer.simpleGroup.psuedoGroup.suffixes = member, manager

    changeLog.consumer.simpleGroup.url = http://localhost:8080
    changeLog.consumer.simpleGroup.username = grouper-admin
    changeLog.consumer.simpleGroup.password = grouper
    # Set to true to test.
    changeLog.consumer.simpleGroup.dryrun = false


    #########################################################################################################################
    #########################################################################################################################

    changeLog.consumer.courseGroups.quartzCron = 0 0 * * * ?
    changeLog.consumer.courseGroups.class = org.sakaiproject.nakamura.grouper.changelog.esb.CourseGroupEsbConsumer

    # You may have to change this stem. 
    # If you do make sure to update nakamura.courses.adhoc.stem and nakamura.courses.provisioned.stem
    changeLog.consumer.courseGroups.elfilter = (event.name          =~ '^edu\\:apps\\:sakaioae\\:provisioned\\:courses.*$' \
                                                 || event.groupName =~ '^edu\\:apps\\:sakaioae\\:provisioned\\:courses.*$'\
                                                 || event.name      =~ '^edu\\:apps\\:sakaioae\\:adhoc\\:courses.*$' \
                                                 || event.groupName =~ '^edu\\:apps\\:sakaioae\\:adhoc\\:courses.*$' ) \
                                               && \
                                               (event.eventType eq 'GROUP_DELETE' \
												 || event.eventType eq 'GROUP_ADD' \
                                                 || event.eventType eq 'MEMBERSHIP_DELETE' \
												 || event.eventType eq 'MEMBERSHIP_ADD')

    changeLog.consumer.courseGroups.adhoc.stem = edu:apps:sakaioae:adhoc:courses
    changeLog.consumer.courseGroups.provisioned.stem = edu:apps:sakaioae:provisioned:courses
changeLog.consumer.courseGroups.createDeleteRole = student

    # Regex indices                                            0       1       2       3       4       5       6
    changeLog.consumer.courseGroups.TemplateGroupIdAdapter.groupName.regex  = edu:apps:sakaioae:provisioned:courses:([^:]+):([^:]+):([^:]+):([^:]+):([^:]+):([^:]+):([^:]+)
    changeLog.consumer.courseGroups.TemplateGroupIdAdapter.groupId.template = 'course_' + g[2] + '_' + g[3] + '_' + g[4] + '_' + g[5] + '_' + g[1] + '_' + g[6]
    changeLog.consumer.courseGroups.TemplateGroupIdAdapter.role.map         = TAs:ta, lecturers:lecturer, students:student, managers:manager
    changeLog.consumer.courseGroups.psuedoGroup.suffixes                    = member, manager, student, lecturer, ta

    changeLog.consumer.courseGroups.nakamura.url = http://localhost:8080
    changeLog.consumer.courseGroups.nakamura.username = grouper-admin
    changeLog.consumer.courseGroups.nakamura.password = grouper
    # Set to true to test.
    changeLog.consumer.courseGroups.nakamura.dryrun = false

    
Run the Grouper Loader

    cd ${GROUPER_HOME}
    ./bin/gsh.sh -loader

Create a SakaiOAE user for the Grouper loader.

You can use the standard registration forms to create the user and set it's password.

Then add it to the administrators group to give it admin rights.

    curl -uadmin:ADMIN_PASSWORD -F:member=grouper-admin \
        http://localhost:8080/system/userManager/group/administrators.update.json

## Links
https://spaces.internet2.edu/display/Grouper/Notifications+(change+log)
http://groups.google.com/group/grouper-users/browse_thread/thread/e1cd180b39583acb?pli=1
