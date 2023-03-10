/**
 *
 *
 */

metadata {
	definition (name: "Shelly Energy Meter Device", namespace: "ShellyUSA-Custom", author: "Corey Cleric") {
        capability "EnergyMeter"
        capability "PowerMeter"
        capability "VoltageMeasurement"
        capability "CurrentMeter"

        attribute "reactive", "number"
        attribute "reactivestatus", "string"
        attribute "energyreturned", "number"
        attribute "pf", "number"
	}

preferences {
        input name: "txtEnable", type: "bool", title: "Enable descriptionText logging", defaultValue: true
    }
}
void updated() {
    log.info "Updated..."
    log.warn "description logging is: ${txtEnable == true}"
}

void installed() {
    log.info "Installed..."
    device.updateSetting("txtEnable",[type:"bool",value:true])
}
