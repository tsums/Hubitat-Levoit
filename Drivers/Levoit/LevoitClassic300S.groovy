/*

MIT License

Copyright (c) Ian Luo

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
SOFTWARE.
*/

metadata {
    definition(
        name: 'Levoit Classic300S Humidifier',
        namespace: 'NiklasGustafsson',
        author: 'Trevor Summerfield',
        description: 'Supports controlling the Levoit Classic300S Humidifier')
        {
            capability 'Switch'
            capability 'SwitchLevel'
            capability 'RelativeHumidityMeasurement'
            capability 'Actuator'
            capability 'Refresh'

            attribute 'targetHumidity', 'number'
            attribute 'mode', 'string'
            attribute 'display', 'string'
            attribute 'lacksWater', 'boolean'
            attribute 'automaticStop', 'boolean'
            attribute 'waterTankLifted', 'boolean'
            attribute 'mistLevel', 'number'
            attribute 'status', 'string'

            command 'setTargetHumidity', [[name: 'TargetHumidity*', type: 'NUMBER', description: 'TargetHumidity (30 - 80)']]
            command 'setDisplay', [[name: 'Display*', type: 'ENUM', description: 'Display', constraints: ['on', 'off']]]
            command 'setMode', [[name: 'Mode*', type: 'ENUM', description: 'Mode', constraints: ['manual', 'sleep', 'auto']]]
            command 'setMistLevel', [[name: 'MistLevel', type: 'NUMBER', description: 'Mist level (1-9)']]
            command 'toggle'
            command 'refresh'
        }

    preferences {
        input('debugOutput', 'bool', title: 'Enable debug logging?', defaultValue: false, required: false)
    }
}

def installed() {
    logDebug "Installed with settings: ${settings}"
    updated()
}

def updated() {
    logDebug "Updated with settings: ${settings}"
    state.clear()
    unschedule()
    initialize()

    runIn(3, refresh)

    // Turn off debug log in 30 minutes
    if (settings?.debugOutput) runIn(1800, logDebugOff)
}

def uninstalled() {
    logDebug 'Uninstalled app'
}

def initialize() {
    logDebug 'initializing'
}

def on() {
    logDebug 'on()'
    handlePower(true)
    handleEvent('switch', 'on')
    refresh()
}

def off() {
    logDebug 'off()'
    handlePower(false)
    handleEvent('switch', 'off')
    refresh()
}

def toggle() {
    logDebug 'toggle()'
    if (device.currentValue('switch') == 'on') {
        off()
    } else {
        on()
    }
}

def setLevel(value) {
    logDebug "setLevel ${value}"
    setMode('manual') // always manual if setLevel() cmd was called
    mistLevel = convertRange(value, 0, 100, 1, 9, true)
    handleMistLevel(mistLevel)
    refresh()
}

def setMistLevel(mistLevel) {
    logDebug "setMistLevel(${mistLevel})"
    setMode('manual')
    handleMistLevel(mistLevel)
    refresh()
}

def setTargetHumidity(targetHumidity) {
    logDebug "setTargetHumidity(${targetHumidity})"
    setMode('auto')
    handleTargetHumidity(targetHumidity)
    refresh()
}

def setMode(mode) {
    logDebug "setMode(${mode})"
    handleMode(mode)
    refresh()
}

def setDisplay(displayOn) {
    logDebug "setDisplay(${displayOn})"

    handleDisplay(displayOn)
    refresh()
}

def handlePower(on) {
    parent.sendBypassRequest(device, [
        data: [enabled: on, id: 0],
        'method': 'setSwitch',
        'source': 'APP'
    ]) {
        resp ->
            if (checkHttpResponse('handleOn', resp)) {
                def operation = on ? 'ON' : 'OFF'
                logDebug "turned ${operation}()"
            }
    }
}

def handleMistLevel(mistLevel) {
    parent.sendBypassRequest(device, [
        data: [id: 0, type: 'mist', level: mistLevel],
        'method': 'setVirtualLevel',
        'source': 'APP'
    ]) {
        resp ->
            if (checkHttpResponse('handleMistLevel', resp)) {
                logDebug 'Set mist level'
            }
    }
}

def handleMode(mode) {
    parent.sendBypassRequest(device, [
          data    : [mode: mode],
          'method': 'setHumidityMode',
          'source': 'APP'
  ]) {
    resp ->
        if (checkHttpResponse('handleMode', resp)) {
            logDebug "Set mode ${mode}"
            result = true
        }
  }
}

def handleDisplay(displayOn) {
    parent.sendBypassRequest(device, [
        data: ['state': displayOn == 'on'],
        'method': 'setDisplay',
        'source': 'APP'
    ]) {
    resp ->
        if (checkHttpResponse('handleDisplay', resp)) {
            logDebug "Set display ${displayOn}"
        }
    }
}

def handleTargetHumidity(targetHumidity) {
    logDebug "handleTargetHumidity(${target_humidity})"

    parent.sendBypassRequest(device, [
    data: ['target_humidity': targetHumidity],
    'method': 'setTargetHumidity',
    'source': 'APP'
  ]) {
    resp ->
        if (checkHttpResponse('handleTargetHumidity', resp)) {
            logDebug "Successfully set target humidity ${targetHumidity}"
        }
  }
}

def refresh() {
    logDebug 'refresh()'
    update()
}

def update() {
    logDebug 'update()'

    parent.sendBypassRequest(device, [
        'method': 'getHumidifierStatus',
        'source': 'APP',
        'data': [: ]
    ]) {
        resp ->
            if (checkHttpResponse('update', resp)) {
                handleUpdateResponse(resp.data.result)
            }
    }
}

def update(status, nightLight) {
    handleUpdateResponse(status)
}

def handleUpdateResponse(response) {
    handleEvent('switch', response.result.enabled ? 'on' : 'off')
    handleEvent('humidity', response.result.humidity)
    handleEvent('mistLevel', response.result.mist_level)
    handleEvent('level', convertRange(response.result.mist_level, 1, 3, 0, 100, true))
    handleEvent('targetHumidity', response.result.configuration.auto_target_humidity)
    handleEvent('mode', response.result.mode)
    handleEvent('display', response.result.display ? 'on' : 'off')
    handleEvent('lacksWater', response.result.water_lacks)
    handleEvent('waterTankLifted', response.result.water_tank_lifted)
    handleEvent('automaticStop', response.result.automatic_stop_reach_target)

    updateStatusString()
}

def updateStatusString() {
    def statusStr = ''

    def lvl = device.currentValue('level')
    def target = device.currentValue('targetHumidity')
    def mode = device.currentValue('mode')

    if (device.currentValue('switch') == 'off') {
        statusStr = 'Off'
    } else if (mode == 'auto' || mode == 'sleep') {
        if (device.currentValue('automaticStop') == "true") {
            statusStr = "Target Reached (${target}%)"
        } else {
            statusStr = "Auto L${lvl} to ${target}%"
        }
    } else {
        statusStr = "Manual L${state.level}"
    }
    handleEvent('status', statusStr)
}

private void handleEvent(name, val) {
    logDebug "handleEvent(${name}, ${val})"
    device.sendEvent(name: name, value: val)
}

private BigDecimal convertRange(BigDecimal val, BigDecimal inMin, BigDecimal inMax, BigDecimal outMin, BigDecimal outMax, Boolean returnInt = true) {
    // Let make sure ranges are correct
    assert(inMin <= inMax)
    assert(outMin <= outMax)

    // Restrain input value
    if (val < inMin) val = inMin
    else if (val > inMax) val = inMax

    val = ((val - inMin) * (outMax - outMin)) / (inMax - inMin) + outMin
    if (returnInt) {
        // If integer is required we use the Float round because the BigDecimal one is not supported/not working on Hubitat
        val = val.toFloat().round().toBigDecimal()
    }

    return (val)
}

def checkHttpResponse(action, resp) {
    if (resp.status == 200 || resp.status == 201 || resp.status == 204) {
        return true
    } else if (resp.status == 400 || resp.status == 401 || resp.status == 404 || resp.status == 409 || resp.status == 500) {
        log.error "${action}: ${resp.status} - ${resp.getData()}"
        return false
    }
    log.error "${action}: unexpected HTTP response: ${resp.status}"
    return false
}

def logDebug(msg) {
    if (settings?.debugOutput) {
        log.debug msg
    }
}

def logError(msg) {
    log.error msg
}

void logDebugOff() {
    if (settings?.debugOutput) device.updateSetting('debugOutput', [type: 'bool', value: false])
}
