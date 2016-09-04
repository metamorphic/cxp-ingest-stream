#!/usr/bin/env python

import sys

counter = 0

#=====================
# Write data to stdout
#=====================
def send(data):
	sys.stdout.write(data)
	sys.stdout.flush()

#===========================================
# Terminate a message using the default CRLF
#===========================================
def eod():
	send("\r\n")

#===========================
# Main - Echo the input
#===========================

while True:
	try:
		data = raw_input()
		if data:
			counter += 1
			send("%s,%d" % (data,counter))
			eod()
	except:
		break
