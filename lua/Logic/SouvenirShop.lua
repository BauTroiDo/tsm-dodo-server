SouvenirShop = {};
local this = SouvenirShop;
this.souvenirList = {};
this.giftsList = {};

function SouvenirShop.SetListOfGiftsInBag()
  table.Clear(this.giftsList);
  local temp = Item.GetBagByCategory(EThingsCategory.Props);

  for i = 1, table.Count(temp) do
    for id in pairs(souvenirDatas) do
      if id == temp[i].Id then
        table.insert(this.giftsList, temp[i]);
      end
    end
  end

  this.SortByQuality(this.giftsList, true);

end

function SouvenirShop.SetSouvenirList()
  table.Clear(this.souvenirList);

  for itemId in pairs(souvenirDatas) do
    table.insert(this.souvenirList, souvenirDatas[itemId]);
  end

  this.SortByQuality(this.souvenirList, true);

end

function SouvenirShop.SortByQuality(list, forShop)
  if forShop then
    table.sort(list,
      function(a, b)
        if souvenirDatas[a.Id] == nil then return end;
        if souvenirDatas[a.Id].quality == souvenirDatas[b.Id].quality then
          return souvenirDatas[a.Id].price < souvenirDatas[b.Id].price
        else
          return souvenirDatas[a.Id].quality < souvenirDatas[b.Id].quality
        end
      end
    );
  else
    table.sort(list,
      function(a, b)
        if souvenirDatas[a.Id] == nil then return end;
        if souvenirDatas[a.Id].quality == souvenirDatas[b.Id].quality then
          return souvenirDatas[a.Id].price > souvenirDatas[b.Id].price
        else
          return souvenirDatas[a.Id].quality > souvenirDatas[b.Id].quality
        end
      end
    );
  end
end

