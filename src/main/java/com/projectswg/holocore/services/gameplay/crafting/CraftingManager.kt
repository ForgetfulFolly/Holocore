/***********************************************************************************
 * Copyright (c) 2023 /// Project SWG /// www.projectswg.com
 *
 * This file is part of Holocore.
 *
 * Holocore is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 ***********************************************************************************/
package com.projectswg.holocore.services.gameplay.crafting

import com.projectswg.holocore.services.gameplay.crafting.resource.CreatureHarvestingService
import com.projectswg.holocore.services.gameplay.crafting.resource.ResourceService
import com.projectswg.holocore.services.gameplay.crafting.session.CraftingSessionService
import com.projectswg.holocore.services.gameplay.crafting.survey.SurveyToolService
import me.joshlarson.jlcommon.control.Manager
import me.joshlarson.jlcommon.control.ManagerStructure

@ManagerStructure(children = [
    CreatureHarvestingService::class,
    ResourceService::class,
    SurveyToolService::class,
    CraftingSessionService::class,
])
class CraftingManager : Manager()
