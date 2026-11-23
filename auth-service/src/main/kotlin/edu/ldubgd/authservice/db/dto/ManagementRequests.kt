package edu.ldubgd.authservice.db.dto

import edu.ldubgd.authservice.db.ManagementMode

data class ManagementModeRequest(val managementMode: ManagementMode)
data class RouteStateRequest(val active: Boolean)
