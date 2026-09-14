#!/bin/bash
set -euo pipefail
# After Beanstalk fetch-config of beanstalk.json (log streaming). append-config
# keeps those logs and adds RAM metrics. fetch-config here would wipe logs.
/opt/aws/amazon-cloudwatch-agent/bin/amazon-cloudwatch-agent-ctl \
  -a append-config -m ec2 -s \
  -c file:/opt/aws/amazon-cloudwatch-agent/etc/cwagent-mem.json
