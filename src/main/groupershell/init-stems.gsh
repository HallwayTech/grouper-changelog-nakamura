grouperSession = GrouperSession.startRootSession()

# Everything goes under nyu
addStem("", "nyu", "nyu")

# Application-specific data
addStem("nyu", "apps", "apps")
addStem("nyu:apps", "atlas", "atlas")
addStem("nyu:apps:atlas", "courses", "courses")

# Institutional data
addStem("nyu", "inst", "inst")
addStem("nyu:inst", "courses", "courses")