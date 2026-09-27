local json = require 'cjson'

ENpcStoreItem = {
  Tonic = 1,    --補品
  Sundries = 2, --雜物
  Material = 3, --材料
  Spar = 4    , --晶石
  LuckyBag = 5, --福袋
}

ENpcStoreEquip = {
  Head = 1, --帽
  Body = 2, --衣
  Hand = 3, --武器
  Wrist = 4, --護脕
  Boots = 5, --鞋子
}

NpcStore = {}
local this = NpcStore;

--save
this.client = {};
this.client.item = {};
this.client.item[ENpcStoreItem.Tonic] = false;
this.client.item[ENpcStoreItem.Sundries] = false;
this.client.item[ENpcStoreItem.Material] = false;
this.client.item[ENpcStoreItem.Spar] = false;
this.client.item[ENpcStoreItem.LuckyBag] = false;

this.client.equip = {};
this.client.equip[ENpcStoreEquip.Head] = {};
this.client.equip[ENpcStoreEquip.Body] = {};
this.client.equip[ENpcStoreEquip.Hand] = {};
this.client.equip[ENpcStoreEquip.Wrist] = {};
this.client.equip[ENpcStoreEquip.Boots] = {};

this.client.equip[ENpcStoreEquip.Head].check = {};
this.client.equip[ENpcStoreEquip.Body].check = {};
this.client.equip[ENpcStoreEquip.Hand].check = {};
this.client.equip[ENpcStoreEquip.Wrist].check = {};
this.client.equip[ENpcStoreEquip.Boots].check = {};

for i = 1, 3 do
  this.client.equip[ENpcStoreEquip.Head].check[i] = false;
  this.client.equip[ENpcStoreEquip.Body].check[i] = false;
  this.client.equip[ENpcStoreEquip.Hand].check[i] = false;
  this.client.equip[ENpcStoreEquip.Wrist].check[i] = false;
  this.client.equip[ENpcStoreEquip.Boots].check[i] = false;
end

function NpcStore.Initialize()
  local jsonText = CGResourceManager.ReadText(string.Concat("NpcStoreSetting", ".txt"));

  if not string.IsNullOrEmpty(jsonText) then
    local data = json.decode(jsonText);

    if data.item ~= nil then
      for k, v in pairs(data.item) do
        this.client.item[tonumber(k)] = v;
      end
    end

    if data.equip ~= nil then
      for k, v in pairs(data.equip) do
        this.client.equip[tonumber(k)] = v;
      end
    end
  end
end

function NpcStore.Save()
  local jsonText = json.encode(this.client);

  CGResourceManager.WriteText(string.Concat("NpcStoreSetting", ".txt"), jsonText);
end

