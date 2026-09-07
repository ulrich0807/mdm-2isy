package com.mdm2isy.agent.service

import android.util.Log
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.mdm2isy.agent.storage.EnrollmentStore

class MdmFirebaseMessagingService : FirebaseMessagingService() {

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        Log.d("FCM", "Nouveau jeton FCM : $token")
        
        // Stocker le token localement pour l'ajouter au heartbeat
        val prefs = getSharedPreferences("mdm_prefs", MODE_PRIVATE)
        prefs.edit().putString("fcm_token", token).apply()
    }

    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        super.onMessageReceived(remoteMessage)
        
        // Si le message contient des données de commande MDM
        if (remoteMessage.data.isNotEmpty()) {
            val commandType = remoteMessage.data["type"]
            Log.d("FCM", "Commande Push reçue : $commandType")
            
            // Relancer le service Agent pour forcer une synchronisation immédiate des commandes
            MdmAgentService.triggerImmediateSync(applicationContext)
        }
    }
}
