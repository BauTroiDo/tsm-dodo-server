UITreasureDetail = {}
UITreasureDetail.__index = UITreasureDetail;

function UITreasureDetail.New(parent)
  local self = {};
  setmetatable(self, UITreasureDetail);

  self.gameObject = poolMgr:Get("BaseUITreasureDetail");
  self.transform = self.gameObject.transform;
  self.rectTransform = self.gameObject:GetComponent("RectTransform");
  self.rectTransform:SetParent(parent.transform);
  self.rectTransform:SetSiblingIndex(0);
  self.rectTransform.localScale = Vector3.one;
  self.rectTransform.anchoredPosition = Vector2.zero;
  self.rectTransform.sizeDelta = Vector2.New(365, 405);
  self.text_Title = self.transform:Find("Image_"):Find("Text_Title"):GetComponent("Text");
  self.items = {};

  local temp = self.transform:Find("Image_"):Find("Scroll View"):Find("DetailContent"):Find("Item").gameObject;
  local itemRoot = self.transform:Find("Image_"):Find("Scroll View"):Find("DetailContent");

  for i = 0, table.Count(Treasure.GetAllBonus()) - 1 do
    local itemTemp = newObject(temp);
    itemTemp.transform:SetParent(itemRoot);
    itemTemp:GetComponent("RectTransform").localScale = Vector2.New(1, 1);
    itemTemp:GetComponent("RectTransform").localPosition = temp:GetComponent("RectTransform").localPosition;

    self.items[i] = {};
    self.items[i].object = itemTemp;
    self.items[i].rectTransform = self.items[i].object:GetComponent("RectTransform");
    self.items[i].text_Title = self.items[i].object.transform:Find("Text_Info"):GetComponent("Text");
    self.items[i].titleSizeDelta = self.items[i].text_Title:GetComponent("RectTransform").sizeDelta;
    self.items[i].text_Detail = self.items[i].object.transform:Find("Text_DetailAttr"):GetComponent("Text");
    self.items[i].detailRectTransform = self.items[i].text_Detail:GetComponent("RectTransform");
    self.items[i].sizeFitter_Detail = self.items[i].text_Detail.gameObject:GetComponent("ContentSizeFitter");
  end

  destroy(temp);

  return self;
end

function UITreasureDetail:Destroy()
  poolMgr:Release("BaseUITreasureDetail", self.gameObject);
end

function UITreasureDetail:ShowInfo(id)
  local bonusData = Treasure.GetAllBonus();

  self.text_Title.text = string.Get(24590);

  for k, v in pairs(bonusData) do
    if id ~= nil then
      self.items[k].object:SetActive(k == id);
    else
      self.items[k].object:SetActive(table.Count(v.data) > 0);
    end

    self.items[k].text_Title.text = Treasure.GetEnvirText(k, true);
    self.items[k].text_Detail.text = "";

    if table.Count(v.data) > 0 then
      for _, v2 in pairs(v.data) do
        self.items[k].text_Detail.text = string.Concat(
          self.items[k].text_Detail.text,
          Treasure.GetRoleText(v2.role),
          ItemData.GetAttrText(v2.att), "+", v2.value,
          "\n");
      end

      self.items[k].sizeFitter_Detail:SetLayoutVertical();
      self.items[k].rectTransform.sizeDelta = Vector2.New(
        self.items[k].titleSizeDelta.x,
        self.items[k].titleSizeDelta.y + self.items[k].detailRectTransform.sizeDelta.y
      );
    end
  end
end