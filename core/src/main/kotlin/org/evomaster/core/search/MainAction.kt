package org.evomaster.core.search

import org.evomaster.core.search.action.Action

abstract class MainAction(children: List<StructuralElement>) : Action(children) {

    final override fun shouldCountForFitnessEvaluations(): Boolean {
        return true
    }

    fun getPreviousMainActions() : List<MainAction>{
        return otherMainActions(true)
    }

    fun getFollowingMainActions() : List<MainAction>{
        return otherMainActions(false)
    }

    private fun otherMainActions(before: Boolean) : List<MainAction> {
        if(!hasLocalId()){
            throw IllegalStateException("No local id defined for this main action")
        }

        val ind = this.getRoot()
        if(ind !is Individual){
            throw IllegalStateException("Action is not mounted inside an individual")
        }

        val all = ind.seeMainExecutableActions()
        val index = all.indexOfFirst { it.getLocalId() == this.getLocalId()}
        if(index < 0){
            throw IllegalStateException("Current action with local id ${getLocalId()} not found among main actions")
        }
        if(before){
            if(index == 0){
                return listOf()
            }
            return all.subList(0,index)
        } else {
            if(index == all.lastIndex){
                return listOf()
            }
            return all.subList(index+1,all.size)
        }
    }
}