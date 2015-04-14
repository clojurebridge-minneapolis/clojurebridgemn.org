#!/usr/bin/python3
# github-push.py

import sys
import os
import time
import string
import cgi
import json
import arrow

def webhook(logname):
    # respond to caller
    # NOTE: this assumes that NOTHING else will go wrong later in this script
    print('Content-type: text/plain\n\nOK')
    # now process input
    log = open(logname, 'a')
    jsontxt = sys.stdin.read()
    payload = json.loads(jsontxt)
    # add environment
    env = {}
    ekeys = ['CONTENT_TYPE', 'HTTP_USER_AGENT', 'HTTP_X_GITHUB_EVENT', 'REQUEST_METHOD', 'QUERY_STRING', 'USERNAME']
    for ekey in ekeys:
        if ekey in os.environ:
            env[ekey] = os.environ[ekey]
    payload['env'] = env
    jsontxt = json.dumps(payload)
    # print('payload = %s' % json.dumps(payload, sort_keys=True, indent=4))
    # save full json
    print(jsontxt, file=log)
    log.close()
    # action
    # pusher = 'uuu' # payload['pusher']['name']
    if 'pusher' in payload:
        pusher = payload['pusher']
    else:
        pusher = None
    if pusher and 'name' in pusher:
        name = pusher['name']
    else:
        name = 'anonymous'
    if 'commits' in payload:
        commits = payload['commits']
    else:
        commits = None
    if commits:
        lastcommit = commits[-1]
    else:
        lastcommit = None
    if lastcommit and 'timestamp' in lastcommit:
        timestamp = lastcommit['timestamp']
        timestamp = arrow.get(timestamp)
        timestamp = timestamp.format("dddd MMM D YYYY @ HH:mm:ss")
    else:
        timestamp = 'now'
    if payload['repository']['name'] == 'clojurebridgemn.org':
        # os.system('cd /srv/clojurebridgemn.org && git pull')
        os.system('/srv/clojurebridgemn.org/bin/update \'%s\' \'%s\'' % (name, timestamp))

def usage():
    """
    If you're a dufus you end up here with obligatory hints and,
    well, that's all folks!
    """
    program = os.path.basename(sys.argv[0])
    print("usage: " + program)
    sys.exit()

if __name__ == '__main__':
    if len(sys.argv) != 1:
        usage()
    dirname = '/var/www/downloads/github-push'
    logname = '%s/%f.json' % (dirname, time.time())
    webhook(logname)
