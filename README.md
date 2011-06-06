## Overview
A Grouper changelog consumer that sends info to SakaiOAE/nakamura via HTTP POSTs.

The Grouper changelog is a list of events in a Grouper system stored in a database table/views.
The Grouper loader can have multiple changelog consumer jobs configured to run a intervals
defined by a quartz scheduler. The Grouper loader keeps track of the last changelog entry
that each consumer successfully processed.

## Installation

Clone this repository and use maven to build the jar.

    git clone URL
    cd grouper-changelog-nakamura
    mvn clean install

Copy the resulting jar to ${GROUPER_HOME}/lib/custom/

Configure the Grouper loader to run your job. Add the following to ${GROUPER_HOME}/conf/grouper-loader.properties

    # The class to run
    changeLog.consumer.nakamura.class = org.sakaiproject.nakamura.grouper.changelog.NakamuraEsbConsumer
    
    # How often to run the job
    changeLog.consumer.nakamura.quartzCron = 0 * * * * ?
    
    # Filter events this job should handle
    changeLog.consumer.nakamura.elfilter = ( (event.name =~ '^edu\\:apps\\:sakai\\:.*$') && \
                                              (event.eventType eq 'GROUP_DELETE' || event.eventType eq 'GROUP_ADD') ) || \
                                              ( (event.groupName =~ '^edu\\:apps\\:sakai\\:.*$') && \ 
                                              (event.eventType eq 'MEMBERSHIP_DELETE' || event.eventType eq 'MEMBERSHIP_ADD'))

    # SakaiOAE location/credentials
    nakamura.url = http://localhost:8080
    nakamura.username = admin
    nakamura.password = admin
    nakamura.basestem = edu:apps:sakai

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
