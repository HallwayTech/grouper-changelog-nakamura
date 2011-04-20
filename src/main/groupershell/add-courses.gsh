grouperSession = GrouperSession.startRootSession()

addStem("nyu:inst:courses", "2011", "2011")
addStem("nyu:inst:courses:2011", "spring", "spring")
addStem("nyu:inst:courses:2011:spring", "cas", "cas")
addStem("nyu:inst:courses:2011:spring:cas", "sampledept", "sampledept")

addStem("nyu:apps:atlas", "courses", "courses")
addStem("nyu:apps:atlas:courses", "2011", "2011")
addStem("nyu:apps:atlas:courses:2011", "spring", "spring")
addStem("nyu:apps:atlas:courses:2011:spring", "cas", "cas")
addStem("nyu:apps:atlas:courses:2011:spring:cas", "sampledept", "sampledept")

###############################################################################
# Institutional side

# Add institutional course stems
addStem("nyu:inst:courses:2011:spring:cas:sampledept", "x00", "x00")
addStem("nyu:inst:courses:2011:spring:cas:sampledept:x00", "101", "101")
addStem("nyu:inst:courses:2011:spring:cas:sampledept:x00:101", "001", "001")

addStem("nyu:inst:courses:2011:spring:cas:sampledept", "x01", "x01")
addStem("nyu:inst:courses:2011:spring:cas:sampledept:x01", "101", "101")
addStem("nyu:inst:courses:2011:spring:cas:sampledept:x01:101", "001", "001")

addStem("nyu:inst:courses:2011:spring:cas:sampledept", "x02", "x02")
addStem("nyu:inst:courses:2011:spring:cas:sampledept:x02", "101", "101")
addStem("nyu:inst:courses:2011:spring:cas:sampledept:x02:101", "001", "001")

addStem("nyu:inst:courses:2011:spring:cas:sampledept", "x03", "x03")
addStem("nyu:inst:courses:2011:spring:cas:sampledept:x03", "101", "101")
addStem("nyu:inst:courses:2011:spring:cas:sampledept:x03:101", "001", "001")

addStem("nyu:inst:courses:2011:spring:cas:sampledept", "x04", "x04")
addStem("nyu:inst:courses:2011:spring:cas:sampledept:x04", "101", "101")
addStem("nyu:inst:courses:2011:spring:cas:sampledept:x04:101", "001", "001")

addStem("nyu:inst:courses:2011:spring:cas:sampledept", "x05", "x05")
addStem("nyu:inst:courses:2011:spring:cas:sampledept:x05", "101", "101")
addStem("nyu:inst:courses:2011:spring:cas:sampledept:x05:101", "001", "001")

# Institutional course groups
addGroup("nyu:inst:courses:2011:spring:cas:sampledept:x00:101:001", "instructors", "instructors")
addGroup("nyu:inst:courses:2011:spring:cas:sampledept:x00:101:001", "students", "students")

addGroup("nyu:inst:courses:2011:spring:cas:sampledept:x01:101:001", "instructors", "instructors")
addGroup("nyu:inst:courses:2011:spring:cas:sampledept:x01:101:001", "students", "students")

addGroup("nyu:inst:courses:2011:spring:cas:sampledept:x02:101:001", "instructors", "instructors")
addGroup("nyu:inst:courses:2011:spring:cas:sampledept:x02:101:001", "students", "students")

addGroup("nyu:inst:courses:2011:spring:cas:sampledept:x03:101:001", "instructors", "instructors")
addGroup("nyu:inst:courses:2011:spring:cas:sampledept:x03:101:001", "students", "students")

addGroup("nyu:inst:courses:2011:spring:cas:sampledept:x04:101:001", "instructors", "instructors")
addGroup("nyu:inst:courses:2011:spring:cas:sampledept:x04:101:001", "students", "students")

addGroup("nyu:inst:courses:2011:spring:cas:sampledept:x05:101:001", "instructors", "instructors")
addGroup("nyu:inst:courses:2011:spring:cas:sampledept:x05:101:001", "students", "students")

###############################################################################
# App side

# Initialize app stems
addStem("nyu:apps:atlas:courses:2011:spring:cas:sampledept", "x00", "x00")
addStem("nyu:apps:atlas:courses:2011:spring:cas:sampledept:x00", "101", "101")
addStem("nyu:apps:atlas:courses:2011:spring:cas:sampledept:x00:101", "001", "001")

addStem("nyu:apps:atlas:courses:2011:spring:cas:sampledept", "x01", "x01")
addStem("nyu:apps:atlas:courses:2011:spring:cas:sampledept:x01", "101", "101")
addStem("nyu:apps:atlas:courses:2011:spring:cas:sampledept:x01:101", "001", "001")

addStem("nyu:apps:atlas:courses:2011:spring:cas:sampledept", "x02", "x02")
addStem("nyu:apps:atlas:courses:2011:spring:cas:sampledept:x02", "101", "101")
addStem("nyu:apps:atlas:courses:2011:spring:cas:sampledept:x02:101", "001", "001")

addStem("nyu:apps:atlas:courses:2011:spring:cas:sampledept", "x03", "x03")
addStem("nyu:apps:atlas:courses:2011:spring:cas:sampledept:x03", "101", "101")
addStem("nyu:apps:atlas:courses:2011:spring:cas:sampledept:x03:101", "001", "001")

addStem("nyu:apps:atlas:courses:2011:spring:cas:sampledept", "x04", "x04")
addStem("nyu:apps:atlas:courses:2011:spring:cas:sampledept:x04", "101", "101")
addStem("nyu:apps:atlas:courses:2011:spring:cas:sampledept:x04:101", "001", "001")

addStem("nyu:apps:atlas:courses:2011:spring:cas:sampledept", "x05", "x05")
addStem("nyu:apps:atlas:courses:2011:spring:cas:sampledept:x05", "101", "101")
addStem("nyu:apps:atlas:courses:2011:spring:cas:sampledept:x05:101", "001", "001")

# Create app course groups
addGroup("nyu:apps:atlas:courses:2011:spring:cas:sampledept:x00:101:001", "managers", "managers")
addGroup("nyu:apps:atlas:courses:2011:spring:cas:sampledept:x00:101:001", "members", "members")
addGroup("nyu:apps:atlas:courses:2011:spring:cas:sampledept:x00:101:001", "managers_sakai3", "managers_sakai3")
addGroup("nyu:apps:atlas:courses:2011:spring:cas:sampledept:x00:101:001", "members_sakai3", "members_sakai3")

addMember("nyu:apps:atlas:courses:2011:spring:cas:sampledept:x00:101:001:members",     "nyu:apps:atlas:courses:2011:spring:cas:sampledept:x00:101:001:members_sakai3")
addMember("nyu:apps:atlas:courses:2011:spring:cas:sampledept:x00:101:001:members", "nyu:inst:courses:2011:spring:cas:sampledept:x00:101:001:students")
addMember("nyu:apps:atlas:courses:2011:spring:cas:sampledept:x00:101:001:members", "nyu:inst:courses:2011:spring:cas:sampledept:x00:101:001:instructors")

addMember("nyu:apps:atlas:courses:2011:spring:cas:sampledept:x00:101:001:managers",    "nyu:apps:atlas:courses:2011:spring:cas:sampledept:x00:101:001:managers_sakai3")
addMember("nyu:apps:atlas:courses:2011:spring:cas:sampledept:x00:101:001:managers", "nyu:inst:courses:2011:spring:cas:sampledept:x00:101:001:instructors")

# Create app course groups
addGroup("nyu:apps:atlas:courses:2011:spring:cas:sampledept:x01:101:001", "managers", "managers")
addGroup("nyu:apps:atlas:courses:2011:spring:cas:sampledept:x01:101:001", "members", "members")
addGroup("nyu:apps:atlas:courses:2011:spring:cas:sampledept:x01:101:001", "managers_sakai3", "managers_sakai3")
addGroup("nyu:apps:atlas:courses:2011:spring:cas:sampledept:x01:101:001", "members_sakai3", "members_sakai3")

addMember("nyu:apps:atlas:courses:2011:spring:cas:sampledept:x01:101:001:members",     "nyu:apps:atlas:courses:2011:spring:cas:sampledept:x01:101:001:members_sakai3")
addMember("nyu:apps:atlas:courses:2011:spring:cas:sampledept:x01:101:001:members", "nyu:inst:courses:2011:spring:cas:sampledept:x01:101:001:students")
addMember("nyu:apps:atlas:courses:2011:spring:cas:sampledept:x01:101:001:members", "nyu:inst:courses:2011:spring:cas:sampledept:x01:101:001:instructors")

addMember("nyu:apps:atlas:courses:2011:spring:cas:sampledept:x01:101:001:managers",    "nyu:apps:atlas:courses:2011:spring:cas:sampledept:x01:101:001:managers_sakai3")
addMember("nyu:apps:atlas:courses:2011:spring:cas:sampledept:x01:101:001:managers", "nyu:inst:courses:2011:spring:cas:sampledept:x01:101:001:instructors")

# Create app course groups
addGroup("nyu:apps:atlas:courses:2011:spring:cas:sampledept:x02:101:001", "managers", "managers")
addGroup("nyu:apps:atlas:courses:2011:spring:cas:sampledept:x02:101:001", "members", "members")
addGroup("nyu:apps:atlas:courses:2011:spring:cas:sampledept:x02:101:001", "managers_sakai3", "managers_sakai3")
addGroup("nyu:apps:atlas:courses:2011:spring:cas:sampledept:x02:101:001", "members_sakai3", "members_sakai3")

addMember("nyu:apps:atlas:courses:2011:spring:cas:sampledept:x02:101:001:members",     "nyu:apps:atlas:courses:2011:spring:cas:sampledept:x02:101:001:members_sakai3")
addMember("nyu:apps:atlas:courses:2011:spring:cas:sampledept:x02:101:001:members", "nyu:inst:courses:2011:spring:cas:sampledept:x02:101:001:students")
addMember("nyu:apps:atlas:courses:2011:spring:cas:sampledept:x02:101:001:members", "nyu:inst:courses:2011:spring:cas:sampledept:x02:101:001:instructors")

addMember("nyu:apps:atlas:courses:2011:spring:cas:sampledept:x02:101:001:managers",    "nyu:apps:atlas:courses:2011:spring:cas:sampledept:x02:101:001:managers_sakai3")
addMember("nyu:apps:atlas:courses:2011:spring:cas:sampledept:x02:101:001:managers", "nyu:inst:courses:2011:spring:cas:sampledept:x02:101:001:instructors")

# Create app course groups
addGroup("nyu:apps:atlas:courses:2011:spring:cas:sampledept:x03:101:001", "managers", "managers")
addGroup("nyu:apps:atlas:courses:2011:spring:cas:sampledept:x03:101:001", "members", "members")
addGroup("nyu:apps:atlas:courses:2011:spring:cas:sampledept:x03:101:001", "managers_sakai3", "managers_sakai3")
addGroup("nyu:apps:atlas:courses:2011:spring:cas:sampledept:x03:101:001", "members_sakai3", "members_sakai3")

addMember("nyu:apps:atlas:courses:2011:spring:cas:sampledept:x03:101:001:members",     "nyu:apps:atlas:courses:2011:spring:cas:sampledept:x03:101:001:members_sakai3")
addMember("nyu:apps:atlas:courses:2011:spring:cas:sampledept:x03:101:001:members", "nyu:inst:courses:2011:spring:cas:sampledept:x03:101:001:students")
addMember("nyu:apps:atlas:courses:2011:spring:cas:sampledept:x03:101:001:members", "nyu:inst:courses:2011:spring:cas:sampledept:x03:101:001:instructors")

addMember("nyu:apps:atlas:courses:2011:spring:cas:sampledept:x03:101:001:managers",  "nyu:apps:atlas:courses:2011:spring:cas:sampledept:x03:101:001:managers_sakai3")
addMember("nyu:apps:atlas:courses:2011:spring:cas:sampledept:x03:101:001:managers", "nyu:inst:courses:2011:spring:cas:sampledept:x03:101:001:instructors")

# Create app course groups
addGroup("nyu:apps:atlas:courses:2011:spring:cas:sampledept:x04:101:001", "managers", "managers")
addGroup("nyu:apps:atlas:courses:2011:spring:cas:sampledept:x04:101:001", "members", "members")
addGroup("nyu:apps:atlas:courses:2011:spring:cas:sampledept:x04:101:001", "managers_sakai3", "managers_sakai3")
addGroup("nyu:apps:atlas:courses:2011:spring:cas:sampledept:x04:101:001", "members_sakai3", "members_sakai3")

addMember("nyu:apps:atlas:courses:2011:spring:cas:sampledept:x04:101:001:members",     "nyu:apps:atlas:courses:2011:spring:cas:sampledept:x04:101:001:members_sakai3")
addMember("nyu:apps:atlas:courses:2011:spring:cas:sampledept:x04:101:001:members", "nyu:inst:courses:2011:spring:cas:sampledept:x04:101:001:students")
addMember("nyu:apps:atlas:courses:2011:spring:cas:sampledept:x04:101:001:members", "nyu:inst:courses:2011:spring:cas:sampledept:x04:101:001:instructors")

addMember("nyu:apps:atlas:courses:2011:spring:cas:sampledept:x04:101:001:managers",     "nyu:apps:atlas:courses:2011:spring:cas:sampledept:x04:101:001:managers_sakai3")
addMember("nyu:apps:atlas:courses:2011:spring:cas:sampledept:x04:101:001:managers", "nyu:inst:courses:2011:spring:cas:sampledept:x04:101:001:instructors")

# Create app course groups
addGroup("nyu:apps:atlas:courses:2011:spring:cas:sampledept:x05:101:001", "managers", "managers")
addGroup("nyu:apps:atlas:courses:2011:spring:cas:sampledept:x05:101:001", "members", "members")
addGroup("nyu:apps:atlas:courses:2011:spring:cas:sampledept:x05:101:001", "managers_sakai3", "managers_sakai3")
addGroup("nyu:apps:atlas:courses:2011:spring:cas:sampledept:x05:101:001", "members_sakai3", "members_sakai3")

addMember("nyu:apps:atlas:courses:2011:spring:cas:sampledept:x05:101:001:members",     "nyu:apps:atlas:courses:2011:spring:cas:sampledept:x05:101:001:members_sakai3")
addMember("nyu:apps:atlas:courses:2011:spring:cas:sampledept:x05:101:001:members", "nyu:inst:courses:2011:spring:cas:sampledept:x05:101:001:students")
addMember("nyu:apps:atlas:courses:2011:spring:cas:sampledept:x05:101:001:members", "nyu:inst:courses:2011:spring:cas:sampledept:x05:101:001:instructors")

addMember("nyu:apps:atlas:courses:2011:spring:cas:sampledept:x05:101:001:managers",     "nyu:apps:atlas:courses:2011:spring:cas:sampledept:x05:101:001:managers_sakai3")
addMember("nyu:apps:atlas:courses:2011:spring:cas:sampledept:x05:101:001:managers", "nyu:inst:courses:2011:spring:cas:sampledept:x05:101:001:instructors")	