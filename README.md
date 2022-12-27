# ShellyEnergyMeterUnofficialDriver
Shelly Energy Meter (EM) or (E3M) Unofficial Driver for Hubitat (no mqtt requirement)

Here is a unofficial driver for Shelly Energy Meter for the Hubitat. It uses a IP address much like the switching products, no need for mqtt.
Install your Energy Meter device on WiFi, upgrade the firmware and set the meter requirements on the Shelly Web GUI.

https://www.shelly.cloud/en-us/products/product-overview/em-120a

https://www.shelly.cloud/en-us/products/product-overview/shelly-3-em

The combines channels are on the primary device driver (2 or 3 phased) and the channels are on thier own child device driver (each single phase).

Install the child device driver name as "Shelly Energy Meter Device"

Add in all the Web Actions URL for callback to Hubitat for power start and stop...

http://172.16.1.111:39501 (your Hubitat IP address)

Set the preferences in the device driver to query every 1 or 5 minutes.

This is built on the same initial code of Scott Grayban's Shelly framework, please thank him for the work. I'm sure eventually there will be a official version.

As far as DHCP settings, I use pre-set DHCP static IPs for all Shelly's on a Juniper router just to give an example.

set system services dhcp static-binding C4:5B:BE:6C:52:7A fixed-address 172.16.2.131

set system services dhcp static-binding C4:5B:BE:6C:52:7A host-name shellyenergy-PoolPump

If your going across a router hop to get the callback to work a hex IP must be used in the device network id (edit). For example: AC100283

Enjoy...

