MyCard = {};  
local this = MyCard;

local authCode = "";

local click = false;

function MyCard.Init()
  LuaHelper.InitMyCard(MyCard.BuySucess, MyCard.BuyFail);
end

function MyCard.Buy(index, tradeType)
  if click then return end;

  local productList = IAPData.GetMyCardProductName();
  local product = productList[index];
  if product == nil then
    return;
  end

  local facTradeSeq = string.format("%s_%s_%s_%s", IAPData.GetMyCardPlateFormName(), IAPData.GetMyCardGameType(), string.format("%s%s%s", CGTimer.serverTime.Year, CGTimer.serverTime.Month, CGTimer.serverTime.Day), this.GenerateRandomString(8));
  local productName = product[2];
  local amount = product[1];
  local customerId = Network.account;
  local dbId = Network.dbId;
  local gsId = Network.serverId;
  local gameType = IAPData.GetMyCardGameType();
  local tradeType = tradeType;
  local currency = IAPData.Currency;
  local checkCode = IAPData.GetMyCardCheckCode()
  local hash = string.lower(LuaHelper.MD5(string.Concat(facTradeSeq, tradeType, dbId, customerId, productName, amount, currency, gameType, gsId, checkCode)));

  param = string.format("?FacTradeSeq=%s&TradeType=%s&ServerId=%s&CustomerId=%s&ProductName=%s&Amount=%s&Currency=%s&GameType=%s&FlowId=%s&hash=%s",
                          facTradeSeq,
                          tradeType,
                          dbId,
                          customerId,
                          productName,
                          amount,
                          currency,
                          gameType,
                          gsId,
                          hash);

  authCode = CGResourceManager.SendSite(string.Concat(IAPData.GetMyCardAuthUrl(), param));
  if authCode == "" or authCode == nil then
    logError("MyCard Cannot pay know");
    return;
  end

  LuaHelper.BuyMyCard(authCode, IAPData.ObjName, IAPData.GetMyCardSandBoxMode());

  click = true;
end

function MyCard.GenerateRandomString(length)
  local result = "";
  local seed = 0;

  math.randomseed(os.time());
  for i = 1, length do
    seed = math.random(0, 1)
    if seed == 0 then
      result = string.Concat(result, string.format("%c", math.random(65, 90)));--A~Z
    elseif seed == 1 then
      result = string.Concat(result, string.format("%c", math.random(97, 122)));--a~z
    end
  end

  return result;
end

function MyCard.BuySucess()
  local param = string.format("?authcode=%s", authCode);

  local result = CGResourceManager.SendSite(string.Concat(IAPData.GetMyCardAddUrl(), param));

  if result == "" or result == nil then
    ShowCenterMessage("MyCard Add Point Error");
  end

  click = false;
end

function MyCard.BuyFail(result)
  ShowCenterMessage(result);

  click = false;
end