TalkingDataPlugin = {}
local this = TalkingDataPlugin;

local isInit = false;

function TalkingDataPlugin.OnStart()
  if Define.IsDebugMode() or isInit then return end

  if LuaHelper.IsAndroidPlatform then
    TalkingDataSDK.OnStart("2E23099D294D44DA81AC46BAEC0D6445", Channel.Android);
  elseif LuaHelper.IsApplePlatform then
    TalkingDataSDK.OnStart("2E23099D294D44DA81AC46BAEC0D6445", Channel.IOS);
  else
    return;
  end

  isInit = true;
end

function TalkingDataPlugin.OnEnd()
  if not isInit then return end

  TalkingDataSDK.OnEnd();
end

function TalkingDataPlugin.SetAccount(id)
  if not isInit then return end

  TalkingDataSDK.SetAccount(id);
end

function TalkingDataPlugin.SetServer(serverId)
  if not isInit then return end

  TalkingDataSDK.SetServer(serverId);
end

function TalkingDataPlugin.SetLevel(level)
  if not isInit then return end
  
  TalkingDataSDK.SetLevel(level);
end

function TalkingDataPlugin.OnCharge(orderId, iapId, currencyAmount, currencyType, virtualCurrencyAmount, paymentType)
  if not isInit then return end

  TalkingDataSDK.OnChargeRequest(orderId, iapId, currencyAmount, currencyType, virtualCurrencyAmount, paymentType);
  TalkingDataSDK.OnChargeSuccess(orderId);
end