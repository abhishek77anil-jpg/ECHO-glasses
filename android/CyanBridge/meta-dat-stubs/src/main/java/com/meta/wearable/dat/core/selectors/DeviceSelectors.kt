package com.meta.wearable.dat.core.selectors

import com.meta.wearable.dat.core.types.DeviceIdentifier

interface DeviceSelector

class AutoDeviceSelector : DeviceSelector

class SpecificDeviceSelector(val identifier: DeviceIdentifier) : DeviceSelector
