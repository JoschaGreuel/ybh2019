package com.example.cloudanchors

import com.google.ar.core.Config
import com.google.ar.core.Session
import com.google.ar.sceneform.ux.ArFragment

class CloudAnchorFragment : ArFragment() {
    override fun getSessionConfiguration(session: Session?): Config {
        planeDiscoveryController.setInstructionView(null)
        planeDiscoveryController.hide()
        val config: Config = super.getSessionConfiguration(session)
        config.cloudAnchorMode = Config.CloudAnchorMode.ENABLED
        return config
    }

}