package tw.tsm.domain.data

/** DVT Data/SouvenirData.lua::New(reader), IOS_Souvenir_C.dat. */
data class SouvenirDef(
    val itemId: Int,
    val quality: Int,
    val payment: Int,
    val price: Int,
    val addCharm: Int,
    val addFriendly: Int,
    val addExp: Int,
    val naming: Boolean,
    val broadcast: Boolean,
    val roleCount: Int,
)
