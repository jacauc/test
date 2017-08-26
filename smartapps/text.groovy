/**
 *  j64Alarm
 *
 *  Copyright 2016 Joe Jarvis
 *
 *  Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 *  in compliance with the License. You may obtain a copy of the License at:
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software distributed under the License is distributed
 *  on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License
 *  for the specific language governing permissions and limitations under the License.
 *
 *  Last Updated : 1/15/2016
 *
 */
definition(
	name: "jacauc Alarm",
	namespace: "jacauc",
	author: "Jodarvis",
	description: "Integd with SmartThings using the j64 Alarm Server. This integration allows you to incorporate and control your existing motion sensors, contact sensors and alarms from the SmartThings Hub.",
	category: "SmartThings Labs",
        iconUrl: "http://cdn.device-icons.smartthings.com/Home/home3-icn.png",
        iconX2Url: "http://cdn.device-icons.smartthings.com/Home/home3-icn@2x.png",
        iconX3Url: "http://cdn.device-icons.smartthings.com/Home/home3-icn@3x.png"
)

mappings {
    /* installDevices is called by the j64 Alarm Server and passes information about
       the various partitions and zones that are defined in your security system.  No
       security or arming codes are transmitted into or stored by this service */
    path("/installDevices") {
        action: [
            POST: "installDevices",
        ]
    }
    
    /* UpdatePartition is called by the j64 Alarm Server when the status of the
       partition has changed.  This information is then propogated to the various
       child devices so it can be reflected in the mobile app  */
    path("/UpdatePartition") {
        action: [
            POST: "updatePartition"
        ]
    }
    
    /* UpdateZone is called by the j64 Alarm Server when the status of a zone 
       has changed.  This information is then propogated to the various child
       devices so it can be reflected in the mobile app */
    path("/UpdateZone") {
        action: [
            POST: "updateZone"
        ]
    }
}

/* *********************************** */
/* Install or update the child devices */
/* *********************************** */
def installDevices() {
    state.j64Server = params.j64Server
    state.j64Port = params.j64Port
    state.j64User = params.j64UserName
    state.j64Password = params.j64Password
    
	log.debug "Install Devices from ${state.j64Server}:${state.j64Port} using id ${state.j64User}"

    hubApiGet("/api/AlarmSystem")
}

def installAllDevices(partitions, zones) {
	def children = getChildDevices()

	// Add any partitions that have not already been created
	partitions.each { p -> 
		def networkId = "partition" + p.id
        def partitionDevice = children.find { item -> item.device.deviceNetworkId == networkId }
        def name = "j64:" + p.name + " Partition"

		if (partitionDevice == null) {
        	log.debug "Add Partition: ${name} => ${networkId}"
	        partitionDevice = addChildDevice("j64", "j64 Partition", networkId, null, [name: "${name}", label:"${name}"])
		}
        else {
        	// Try to update the name of the partition
			partitionDevice.name = name
            partitionDevice.label = name
        	log.debug "Update Partition: ${name} => ${networkId}"
        }
            
        // always set the current status for the partition
        partitionDevice.setAlarm(p.alarmOn.toString(), p.isArmed, p.armingMode)
        partitionDevice.setMode(p.armingMode, p.readyToArm)
        
        // Add an alarm device for this partition
		networkId = "alarm" + p.id
        def alarmDevice = children.find { item -> item.device.deviceNetworkId == networkId }
        name = "j64:" + p.name + " Alarm"

		if (alarmDevice == null) {
        	log.debug "Add Alarm: ${name} => ${networkId}"
	        alarmDevice = addChildDevice("j64", "j64 Alarm", networkId, null, [name: "${name}", label:"${name}"])
		}
        else {
        	// Try to update the name of the alarm
			alarmDevice.name = name
            alarmDevice.label = name
        }
            
        // always set the current status for the alarm
        alarmDevice.setAlarm(p.alarmOn.toString())
	}
    
    zones.each { z ->
		def networkId = "zone" + z.id
        def zoneDevice = children.find { item -> item.device.deviceNetworkId == networkId }
        def name = "j64:" + z.name
        def zoneType = "j64 Contact Zone"
        if ( z.zoneType == 1 )
           zoneType = "j64 Motion Zone"
        
        if (zoneDevice == null) {
        	log.debug "Add Zone: ${name} => ${networkId}"
	        zoneDevice = addChildDevice("j64", zoneType, networkId, null, [name: "${name}", label:"${name}"])
        }    
        else {
        	// Try to update the name of the partition
			zoneDevice.name = name
            zoneDevice.label = name
            
        	log.debug "Update Zone: ${name} => ${networkId}"

            // Note: we cannot change the device type via the API
            //zoneDevice.typeName = zoneType
        }
        
        // set the current status for the zone
        zoneDevice.setState(z.status)
    }
    
    // Delete any Zones and Parttions that are not in the alarm system configuration
    children.each { d ->
    
    	if ( d.device.deviceNetworkId.startsWith("zone") ) {
			def deviceZoneId = d.device.deviceNetworkId.replaceAll("zone","")
    		def alarmSystemZone = zones.find { z -> "${z.id}" == "${deviceZoneId}" }
            if (alarmSystemZone == null) {
               log.debug "Removing zone: ${d.deviceNetworkId} because it was not in the j64 alarm system config"
               deleteChildDevice(d.device.deviceNetworkId)
        	}
        }
	    
    	if ( d.device.deviceNetworkId.startsWith("partition") ) {
			def devicePartitionId = d.device.deviceNetworkId.replaceAll("partition","")
    		def alarmSystemPartition = partitions.find { p -> "${p.id}" == "${devicePartitionId}" }
            if (alarmSystemPartition == null) {
               log.debug "Removing partition: ${d.deviceNetworkId} because it was not in the j64 alarm system config"
               deleteChildDevice(d.device.deviceNetworkId)
        	}
        }
	}
}

/* *********************************** */
/* Update the status of the partition  */
/* *********************************** */
def updatePartition(evt) {
    def Id = params.Id
	def Name = params.Name
	def ReadyToArm = params.ReadyToArm
    def IsArmed = params.IsArmed.toString()
    def AlarmOn = params.AlarmOn.toString()
    def ArmingMode = params.ArmingMode
    def SHMIntegrationEnabled = params.SHMIntegrationEnabled
    
	def children = getChildDevices()
    def networkId = "partition${Id}"
    def partitionDevice = children.find { item -> item.device.deviceNetworkId == networkId }
    if (partitionDevice != null) {
		log.debug "updatePartition: ${Id} ${Name} ReadyToArm: ${ReadyToArm} IsArmed: ${IsArmed} AlarmOn: ${AlarmOn} ArmingMode: ${ArmingMode}  SHMIntegrationEnabled: ${SHMIntegrationEnabled}"
		partitionDevice.setAlarm(AlarmOn, IsArmed, ArmingMode)
        partitionDevice.setMode(ArmingMode, ReadyToArm)
        
        // Keep smart home monitor (SHM) in synch with the j64 alarm server
        // We sync SHM with partition 1 in the alarm system since it does
        // not support multiple partitions
        if (SHMIntegrationEnabled) {
            if ("${Id}" == "1") {
            	if (IsArmed == "False") {
                    if (ArmingMode == "NotArmed") {
                        sendLocationEvent(name: "alarmSystemStatus", value: "off", data: "From-UpdatePartition")
                    }
                }
                
                if (IsArmed == "True") {
                    if (ArmingMode == "ExitDelayInProgress") {
                        sendLocationEvent(name: "alarmSystemStatus", value: "stay", data: "From-UpdatePartition")
                    }
                    if (ArmingMode == "Stay") {
                        sendLocationEvent(name: "alarmSystemStatus", value: "stay", data: "From-UpdatePartition")
                    }
                    if (ArmingMode == "Away") {
                        sendLocationEvent(name: "alarmSystemStatus", value: "away", data: "From-UpdatePartition")
                    }
                }
            }
        }
	}
    
    networkId = "alarm${Id}"
    def alarmDevice = children.find { item -> item.device.deviceNetworkId == networkId }
    if (alarmDevice != null) {
		alarmDevice.setAlarm(AlarmOn)
	}
}

/* These methods call the j64 Alarm Server and arm or disarm the partiion */
def armPartition(partitionId, stayAway) {
	if (stayAway == "away")
	    hubApiGet("/api/AlarmSystem/AwayArm/${partitionId}")
	else
    	hubApiGet("/api/AlarmSystem/StayArm/${partitionId}")
}

def disarmPartition(partitionId) {
   	hubApiGet("/api/AlarmSystem/Disarm/${partitionId}")
}

def soundAlarm(partitionId) {
    hubApiGet("/api/AlarmSystem/SoundAlarm")
}

def refreshPartition(partitionId) {
    hubApiGet("/api/AlarmSystem")
}

/* ****************************** */
/* Update the status of the zone  */
/* ****************************** */
def updateZone(evt) {
    def Id = params.Id
    def Name = params.Name
	def Status = params.Status
    
	def children = getChildDevices()
    def networkId = "zone${Id}"
    def zoneDevice = children.find { item -> item.device.deviceNetworkId == networkId }
    if (zoneDevice != null) {
		log.debug "updateZone: ${Id} ${Name}->${Status}"
    	zoneDevice.setState(Status)
	}
}

/* These methods call the j64 Alarm Server to bypass the zone so the partition can be armed  */
def bypassZone(zoneId) {
    hubApiGet("/api/AlarmSystem/BypassZone/${zoneId}")
}

def refreshZone(zoneId) {
    hubApiGet("/api/AlarmSystem")
}

/* ************** */
/* Initialization */
/* ************** */
def installed() { 
	initialize() 
}

def updated() { 
	unsubscribe()
  	unschedule()
	initialize() 
}

def uninstalled() {
	unschedule()
}	

def initialize() {
	subscribe(location, null, localLanHandler, [filterEvents:false])
	subscribe(location, "alarmSystemStatus", shmModeChangeHandler, [filterEvents:false])
}

def refresh() {   
    hubApiGet("/api/AlarmSystem")
}

/* ***************************************************** */
/* Handle event from the j64AlarmServer on the local LAN */
/* ***************************************************** */
def localLanHandler(evt) {

	// Only handle messages from the j64AlarmServer
	def msg = parseLanMessage(evt.description)
    
    // The request must be valid json format
	if (msg.json == null) {
	    return
    }
    
    def FromHost = msg.json.fromHost
    def Route = msg.json.route
    
    // The request must have a from host property that matches the alarm server
    if (FromHost != j64AlarmServerAddress()) {
		log.debug "localLanHandler: FromHost mismatch=${FromHost} <> ${j64AlarmServerAddress()}"
	    return
    }

    // Ensure that the route property passed from the j64 server is what we expect     
    if (Route == "/api/AlarmSystem")
       installAllDevices(msg.json.response.partitions, msg.json.response.zones)
	else
		log.debug "localLanHandler: Route mismatch=${Route} <> /api/AlarmSystem"
}

def j64AlarmServerAddress() {
	return state.j64Server + ":" + state.j64Port
}

/* ************************************** */
/* Handle event when the SHM mode changes */
/* ************************************** */
def shmModeChangeHandler(evt) {

  // Unfortunately we have to assume partition 1 for the smart home monitor since it does not support
  // multiple partitions.  Maybe this could change based on the location??
	def Id = 1
	def networkId = "partition${Id}"

    if (evt.data == "From-UpdatePartition") {
    	log.debug "shmModeChangeHandler: called from UpdatePartition so partition status is already set properly"
		return
    }
    
	def partitionDevice = getChildDevices().find { item -> item.device.deviceNetworkId == networkId }
    if (partitionDevice != null) {
    	def mode = partitionDevice.currentAlarm.toLowerCase() 

		if (evt.value == "off"){
		   log.debug "shmModeChangeHandler: disarm the partition"
		   disarmPartition(1);
		}

		if (evt.value == "stay"){
		   log.debug "shmModeChangeHandler: stay arm the partition"
		   armPartition(1, "stay");
		}

		if (evt.value == "away") {
		   log.debug "shmModeChangeHandler: away arm the partition"
		   armPartition(1, "away");
		}
	}
}

/* **************** */
/* Helper Functions */
/* **************** */
private hubApiGet(apiPath) {	

	def userpassascii = "${state.j64User}:${state.j64Password}"
	def userpass = "Basic " + userpassascii.encodeAsBase64().toString()
    
    def headers = [:] 
    headers.put("HOST", j64AlarmServerAddress())
    headers.put("Authorization", userpass)

	def result = new physicalgraph.device.HubAction(
 		   	method: "GET",
    		path: apiPath,
    		headers: headers
		)
    sendHubCommand(result)
}
