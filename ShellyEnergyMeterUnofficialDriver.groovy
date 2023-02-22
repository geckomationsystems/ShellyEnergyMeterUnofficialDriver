/**
 *   
 *  Shelly Energy Meter Driver
 *
 *  Copyright © 2018-2019 Scott Grayban
 *  Copyright © 2020 Allterco Robotics US
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
 * Hubitat is the Trademark and intellectual Property of Hubitat Inc.
 * Shelly is the Trademark and Intellectual Property of Allterco Robotics Ltd
 *  
 *-------------------------------------------------------------------------------------------------------------------
 *
 * See all the Shelly Products at https://shelly.cloud/
 *
 *  Changes:
 *  
 *  1.0.0 - Initial code - Unofficial Custom Driver - Code borrowed and modified to support the Shelly Motion 2 Devices
 *              Removed the Check FW and Upgrade features. /Corey
 *  1.0.1 - Added 24hr reset totals
 *
 */

       

import groovy.json.*
import groovy.transform.Field

def setVersion(){
	state.Version = "1.0.1"
	state.InternalName = "ShellyEnergyMeterUnofficialDriver"
}

metadata {
	definition (
		name: "Shelly Energy Meter",
		namespace: "ShellyUSA-Custom",
		author: "Scott Grayban / Corey J Cleric"
		)
	{
        capability "Refresh"
        capability "Switch"
        capability "Polling"
        capability "SignalStrength"
        capability "Initialize"
        capability "EnergyMeter"
        capability "PowerMeter"
        capability "VoltageMeasurement"
        capability "CurrentMeter"

        command "RebootDevice"
        command "ResetDeviceData"
        command "ResetTotals"
        //command "TimerAutoOff", [[name:"timerautooff", type:"NUMBER", description:"Timer in seconds turn off switch"]] 
        //command "TimerAutoOn", [[name:"timerautoon", type:"NUMBER", description:"Timer in seconds turn on switch"]] 

        attribute "WiFiSignal", "string"
        
        attribute "reactive", "number"
        attribute "reactivestatus", "string"
        attribute "energyreturned", "number"
        attribute "pf", "number"
        
}
    

	preferences {
	def refreshRate = [:]
		refreshRate << ["1 min" : "Refresh every minute"]
        refreshRate << ["5 min" : "Refresh every 5 minutes"]
		refreshRate << ["15 min" : "Refresh every 15 minutes"]
		refreshRate << ["30 min" : "Refresh every 30 minutes"]
		refreshRate << ["manual" : "Manually or Polling Only"]

	input("ip", "string", title:"IP", description:"Shelly IP Address", defaultValue:"" , required: true)
	input name: "username", type: "text", title: "Username:", description: "(blank if none)", required: false
	input name: "password", type: "password", title: "Password:", description: "(blank if none)", required: false
    input name: "isrounded", type: "bool", title: "Rounded Numbers", defaultValue: true
    input name: "dototalsreset", type: "bool", title: "Reset Energy Totals at 12AM", defaultValue: true
    input("refresh_Rate", "enum", title: "Device Refresh Rate", description:"<font color=red>!!WARNING!!</font><br>DO NOT USE if you have over 50 Shelly devices.", options: refreshRate, defaultValue: "manual")
    input name: "debugOutput", type: "bool", title: "Enable debug logging?", defaultValue: true
	input name: "debugParse", type: "bool", title: "Enable JSON parse logging?", defaultValue: true
	input name: "txtEnable", type: "bool", title: "Enable descriptionText logging", defaultValue: true
	}
}

def initialize() {
	log.info "Shelly Energy Meter IP ${ip} is initializing..."
    getSettings()
    log.info "Shelly Energy Meter IP ${ip} device type = ${state.devicetype} channels = ${state.channels}."
    if (state.channels) {
        String thisId = device.id
        for (int myindex = 1; myindex <= state.channels; myindex++) {
            if (!getChildDevice("${thisId}-Channel${myindex}")) {
                addChildDevice("ShellyUSA-Custom", "Shelly Energy Meter Device", "${thisId}-Channel${myindex}", [name: "Channel${myindex}", isComponent: true])
                log.info "Shelly Energy Meter IP ${ip} installing child ${thisId}-Channel${myindex}."
                }
            }
        }    
    //def time_off = "23:59"
    //time_off = new Date(timeToday(time_off).time)
    updated()
    //runIn(10,getMeterStatus)
}

def installed() {
    log.debug "Shelly Energy Meter IP ${ip} installed."
    state.DeviceName = "NotSet"
}

def uninstalled() {
    unschedule()    
    removeChildDevices(getChildDevices())
    log.debug "Shelly Energy Meter IP ${ip} uninstalled."
}

private removeChildDevices(delete) {
	delete.each {deleteChildDevice(it.deviceNetworkId)}
}

def updated() {
    if (txtEnable) log.info "Shelly Energy Meter IP ${ip} preferences updated."
    log.warn "Shelly Motion 2 IP ${ip} debug logging is: ${debugOutput == true}"
    unschedule()
    //dbCleanUp()
    schedule("0 0 0 ? * * *",runResetTotals)
    switch(refresh_Rate) {
		case "1 min" :
			runEvery1Minute(autorefresh)
			break
        case "5 min" :
			runEvery5Minutes(autorefresh)
			break
		case "15 min" :
			runEvery15Minutes(autorefresh)
			break
		case "30 min" :
			runEvery30Minutes(autorefresh)
			break
		case "manual" :
			unschedule(autorefresh)
            log.info "Autorefresh disabled"
            break
	}
	if (txtEnable) log.debug ("Shelly Energy Meter IP ${ip} auto Refresh set for every ${refresh_Rate} minute(s).")

    if (debugOutput) runIn(1800,logsOff) //Off in 30 minutes
    if (debugParse) runIn(300,logsOff) //Off in 5 minutes
    state.LastRefresh = new Date().format("YYYY/MM/dd \n HH:mm:ss", location.timeZone)
    refresh()
}

private dbCleanUp() {
    state.clear()
}


def runResetTotals() {
    if (dototalsreset) {
        logDebug "Shelly Energy Meter IP ${ip} totals reset."
        ResetTotals()
    }
}    

def Refresh() { refresh() }

def refresh() {
    log.info "Shelly Energy Meter IP ${ip} refresh."
    getMeterStatus()
}

def getMeterStatus() {
    def params = [uri: "http://${username}:${password}@${ip}/status"]
        
try {
    httpGet(params) {
        resp -> resp.headers.each {
        logJSON "Shelly Energy Meter IP ${ip} response: ${it.name} : ${it.value}"
    }
        obs = resp.data
        logJSON "Shelly Energy Meter IP ${ip} params: ${params}"
        logJSON "Shelly Energy Meter IP ${ip} response contentType: ${resp.contentType}"
	    logJSON "Shelly Energy Meter IP ${ip} response data: ${resp.data}"

        if (obs.relays[0].ison == false) state.switch = "off" else state.switch = "on" 

        sendEvent(name: "switch", value: state.switch)
        
        if (state.channels) {
            tpower = 0; tvoltage = 0; tamperage = 0; treturned = 0; tenergy = 0; treactive = 0
            for (int myindex = 1; myindex <= state.channels; myindex++) {
                mybox = myindex - 1
                state."wattschannel${myindex}" = getRounded(obs.emeters[(mybox)].power) 
                tpower = tpower + state."wattschannel${myindex}"
                state."reactivechannel${myindex}" = obs.emeters[(mybox)].reactive
                treactive = treactive + state."reactivechannel${myindex}"
                state."pfchannel${myindex}" = obs.emeters[(mybox)].pf
                state."voltagechannel${myindex}" = getRounded(obs.emeters[(mybox)].voltage)  
                tvoltage = tvoltage + state."voltagechannel${myindex}"
                state."wattsperhourchannel${myindex}" = getRounded(obs.emeters[(mybox)].total) 
                tenergy = tenergy + state."wattsperhourchannel${myindex}"
                state."returnedchannel${myindex}" = getRounded(obs.emeters[(mybox)].total_returned)  
                treturned = treturned + state."returnedchannel${myindex}"

                if (obs.emeters[(mybox)].power != 0) { 
                    state."ampschannel${myindex}" = getRounded2((obs.emeters[(mybox)].power / obs.emeters[(mybox)].voltage))
                } else { state."ampschannel${myindex}" = 0 }
                tamperage = tamperage + state."ampschannel${myindex}"
            }
            state.power = getRounded(tpower)
            state.voltage = getRounded(tvoltage)
            state.amperage = getRounded2(tamperage)
            state.energy = getRounded(tenergy)
            state.returned = getRounded(treturned)
            state.reactive = treactive
            state.reactivestatus = getReactiveText(state.reactive)
            
            sendEvent(name: "reactivestatus", value: state.reactivestatus)
            sendEvent(name: "reactive", value: state.reactive)      
            sendEvent(name: "energyreturned", value: state.returned, unit: "kWh") 
        
            sendEvent(name: "power", value: state.power, unit: "W") 
            sendEvent(name: "energy", value: state.energy, unit: "kWh") 
            sendEvent(name: "voltage", value: state.voltage, unit: "V") 
            sendEvent(name: "amperage", value: state.amperage, unit: "A") 
        }
   
/*
-30 dBm Excellent | -67 dBm     Good | -70 dBm  Poor | -80 dBm  Weak | -90 dBm  Dead
*/

        if (signal <= 0 && signal >= -70) {
            sendEvent(name:  "WiFiSignal", value: "<font color='green'>Excellent</font>", isStateChange: true);
        } else
        if (signal < -70 && signal >= -80) {
            sendEvent(name:  "WiFiSignal", value: "<font color='green'>Good</font>", isStateChange: true);
        } else
        if (signal < -80 && signal >= -90) {
            sendEvent(name: "WiFiSignal", value: "<font color='yellow'>Poor</font>", isStateChange: true);
        } else 
        if (signal < -90 && signal >= -100) {
            sendEvent(name: "WiFiSignal", value: "<font color='red'>Weak</font>", isStateChange: true);
        }
        state.rssi = obs.wifi_sta.rssi
        sendEvent(name: "rssi", value: state.rssi)

} // End try
       } catch (e) {
           log.error "Shelly Energy Meter IP ${ip} getMeterStatus something went wrong: $e"
       }
       runIn(3,updateChildren)
} // End getMeterStatus

    
def updateChildren() {
    if (state.channels) {
        String thisId = device.id
        for (int myindex = 1; myindex <= state.channels; myindex++) {
            child = getChildDevice("${thisId}-Channel${myindex}")
            if (child) {
                child.sendEvent(name: "power", value: state."wattschannel${myindex}", unit: "W") 
                child.sendEvent(name: "energy", value: state."wattsperhourchannel${myindex}", unit: "kWh") 
                child.sendEvent(name: "voltage", value: state."voltagechannel${myindex}", unit: "V") 
                child.sendEvent(name: "amperage", value: state."ampschannel${myindex}", unit: "A") 
                child.sendEvent(name: "reactive", value: state."reactivechannel${myindex}") 
                child.sendEvent(name: "pf", value: state."pfchannel${myindex}") 
                child.sendEvent(name: "reactivestatus", value: getReactiveText(state."reactivechannel${myindex}")) 
                child.sendEvent(name: "energyreturned", value: state."returnedchannel${myindex}") 
            } else { log.error "Shelly Energy Meter IP ${ip} updateChildren something went wrong: $e re-initialize please." }
        }
    }
}


def getReactiveText(myreactive) {
    if (myreactive > 0) { return "inductive" } 
    else if (myreactive < 0) { return "capacitive" }
    else { return "undetermined" }
}

def getRounded(myvalue) {
    if (isrounded) return Math.round(myvalue) else return myvalue 
}

def getRounded2(myvalue) {
    if (isrounded) return ((Math.round(myvalue) * 10) / 10) else return myvalue 
}

def getSettings(){

    logDebug "Shelly Energy Meter IP ${ip} get settings called"
    //getSettings()
    def params = [uri: "http://${username}:${password}@${ip}/settings"]

try {
    httpGet(params) {
        resp -> resp.headers.each {
        logJSON "Shelly Energy Meter IP ${ip} response: ${it.name} : ${it.value}"
    }
        obs = resp.data
        logJSON "Shelly Energy Meter IP ${ip} params: ${params}"
        logJSON "Shelly Energy Meter2 IP ${ip} response contentType: ${resp.contentType}"
	    logJSON "Shelly Energy Meter IP ${ip} response data: ${resp.data}"

        
        updateDataValue("Device Name", obs.name)
        updateDataValue("FW Version", obs.fw)
        updateDataValue("Device Type", obs.device.type)
        updateDataValue("Hostname", obs.device.hostname)
        updateDataValue("MAC", obs.device.mac)
        updateDataValue("SSID", obs.wifi_sta.ssid)
        updateDataValue("Timezone", obs.timezone)
        //updateDataValue("Daylight Savings", obs.tz_dst)
        
        state.devicetype = obs.device.type
        if (state.devicetype == "SHEM") { state.channels = 2 } 
        else if (state.devicetype == "SH3EM") { state.channels = 3 }
        else { state.channels = 0 }
        
    } // End try
       } catch (e) {
           log.error "Shelly Energy Meter IP ${ip} getSettings something went wrong: $e"
       }
    
} // End Device Info


// Parse incoming device messages to generate events
def parse(String description) {
    log.info "Shelly Energy Meter IP ${ip} recieved callback message."
    getMeterStatus()
}


def ping() {
	logDebug "Shelly Energy Meter IP ${ip} recieved ping."
	poll()
}

def logsOff(){
	log.warn "Shelly Energy Meter IP ${ip} debug logging auto disabled..."
	device.updateSetting("debugOutput",[value:"false",type:"bool"])
	device.updateSetting("debugParse",[value:"false",type:"bool"])
}

def autorefresh() {
    if (locale == "UK") {
	logDebug "Shelly Energy Meter IP ${ip} Get last UK Date DD/MM/YYYY"
	state.LastRefresh = new Date().format("d/MM/YYYY \n HH:mm:ss", location.timeZone)
	sendEvent(name: "LastRefresh", value: state.LastRefresh, descriptionText: "Last refresh performed")
	} 
	if (locale == "US") {
	logDebug "Shelly Energy Meter IP ${ip} Get last US Date MM/DD/YYYY"
	state.LastRefresh = new Date().format("MM/d/YYYY \n HH:mm:ss", location.timeZone)
	sendEvent(name: "LastRefresh", value: state.LastRefresh, descriptionText: "Last refresh performed")
	}
    refresh()
}

private logJSON(msg) {
	if (settings?.debugParse || settings?.debugParse == null) {
	log.info "Shelly Energy Meter IP ${ip} $msg"
	}
}

private logDebug(msg) {
	if (settings?.debugOutput || settings?.debugOutput == null) {
	log.debug "Shelly Energy Meter IP ${ip} $msg"
	}
}

// handle commands
//RK Updated to include last refreshed
def poll() {
	if (locale == "UK") {
	logDebug "Shelly Energy Meter IP ${ip} Get last UK Date DD/MM/YYYY"
	state.LastRefresh = new Date().format("d/MM/YYYY \n HH:mm:ss", location.timeZone)
	sendEvent(name: "LastRefresh", value: state.LastRefresh, descriptionText: "Last refresh performed")
	} 
	if (locale == "US") {
	logDebug "Shelly Energy Meter IP ${ip} Get last US Date MM/DD/YYYY"
	state.LastRefresh = new Date().format("MM/d/YYYY \n HH:mm:ss", location.timeZone)
	sendEvent(name: "LastRefresh", value: state.LastRefresh, descriptionText: "Last refresh performed")
	}
	if (txtEnable) log.info "Shelly Energy Meter IP ${ip} executing 'poll'" //RK
	refresh()
}

def on() {
    if (txtEnable) log.info "Shelly Energy Meter IP ${ip} switch on."
        def params = [uri: "http://${username}:${password}@${ip}/relay/0?turn=on"]
try {
    httpGet(params) {
        resp -> resp.headers.each {
        logDebug "Shelly Energy Meter IP ${ip} response: ${it.name} : ${it.value}"
    }
} // End try
        
} catch (e) {
        log.error "Shelly Energy Meter IP ${ip} something went wrong: $e"
    }
    state.switch = "on"
    sendEvent(name: "switch", value: "on")
    runIn(15,refresh)
}

def off() {
    if (txtEnable) log.info "Shelly Energy Meter IP ${ip} switch off."
    def params = [uri: "http://${username}:${password}@${ip}/relay/0?turn=off"]
try {
    httpGet(params) {
        resp -> resp.headers.each {
        logDebug "Shelly Energy Meter IP ${ip} response: ${it.name} : ${it.value}"
    }
} // End try
        
} catch (e) {
        log.error "Shelly Energy Meter IP ${ip} something went wrong: $e"
    }
    state.switch = "off"
    sendEvent(name: "switch", value: "off")
    runIn(15,refresh)
}


def RebootDevice() {
    if (txtEnable) log.info "Shelly Energy Meter IP ${ip} rebooting device"
    def params = [uri: "http://${username}:${password}@${ip}/reboot"]
try {
    httpGet(params) {
        resp -> resp.headers.each {
        logDebug "Shelly Energy Meter IP ${ip} response: ${it.name} : ${it.value}"
    }
} // End try
        
} catch (e) {
        log.error "Shelly Energy Meter IP ${ip} something went wrong: $e"
    }
    runIn(15,refresh)
}

def ResetDeviceData() {
        if (txtEnable) log.info "Shelly Energy Meter IP ${ip} Resetting Device Data"
    def params = [uri: "http://${username}:${password}@${ip}/reset_data"]
try {
    httpGet(params) {
        resp -> resp.headers.each {
        logDebug "Shelly Energy Meter IP ${ip} response: ${it.name} : ${it.value}"
    }
} // End try
        
} catch (e) {
        log.error "Shelly Energy Meter IP ${ip} something went wrong: $e"
    }
    runIn(15,refresh)
}
    
def ResetTotals() {
    if (txtEnable) log.info "Shelly Energy Meter IP ${ip} Resetting Totals"
    def params = [uri: "http://${username}:${password}@${ip}/emeter/0?reset_totals=0"]
    try {
        httpGet(params) {
            resp -> resp.headers.each {
                logDebug "Shelly Energy Meter IP ${ip} response: ${it.name} : ${it.value}"
                }
    } // End try
    } catch (e) {
        log.error "Shelly Energy Meter IP ${ip} something went wrong: $e"
    }

    params = [uri: "http://${username}:${password}@${ip}/emeter/1?reset_totals=0"]
    try {
        httpGet(params) {
            resp -> resp.headers.each {
            logDebug "Shelly Energy Meter IP ${ip} response: ${it.name} : ${it.value}"
        }
    } // End try
    } catch (e) {
        log.error "Shelly Energy Meter IP ${ip} something went wrong: $e"
    }
    runIn(15,refresh)
}  
    
