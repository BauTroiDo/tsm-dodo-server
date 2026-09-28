PointCard = {}
local this = PointCard;

PointCard.Kind = {
  ULong = 1,
  MyCard = 2, 
  NineCard = 3,
}

local cardKind;
local cardNo;
local cardPas;

function PointCard.Buy(kind, no, pas)
  if kind == nil then logError("cardKind == nil") return end

  cardKind = kind;
  cardNo = no;
  cardPas = pas;

  if not PointCard.IsCardLengthValid(cardNo) then
    ShowCenterMessage(string.Get(21603));
    return;
  end

  if not PointCard.IsCardLengthValid(cardPas) then
    ShowCenterMessage(string.Get(21603));
    return;
  end

  this.SendPointCard();
end

function PointCard.IsCardLengthValid(text)
  local result = false

  if text == nil or text == "" then
    return result;
  end

  if cardKind == PointCard.Kind.ULong then
    if string.GetLength(text) ~= 10 then
      result = false;
    else
      result = true;
    end 
  elseif cardKind == PointCard.Kind.NineCard or cardKind == PointCard.Kind.MyCard then
    result = true;
  end

  return result;
end

function PointCard.SendPointCard()
--[[
C:035-005 <卡號儲值> 卡號長度(1)+ 卡號(?) +密碼(?) +種類(1)
  種類 1:遊龍10碼  
  種類 2:mycard 16碼  
  種類 3:980x
]]--
  if cardNo == "" or cardPas == "" then
    return;
  end

  sendBuffer:Clear();
  sendBuffer:WriteByte(string.GetLength(cardNo));
  sendBuffer:WriteString(cardNo);
  sendBuffer:WriteByte(string.GetLength(cardPas));
  sendBuffer:WriteString(cardPas);
  sendBuffer:WriteUInt16(cardKind);
  Network.Send(35, 5, sendBuffer);
end