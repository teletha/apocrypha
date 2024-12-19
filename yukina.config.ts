export const YukinaConfig = {
  Title: "Yukina",
  SubTitle: "Yukina Template", // doesn't work when UseHitokotoSubtitle is true
  UseHitokotoSubtitle: true,
  Description: "Demo Site",
  IconfontURL: "//at.alicdn.com/t/c/font_4507154_ptdxadr7249.css",
  Avatar: "https://s2.loli.net/2024/12/06/zNY2H3mcrwhpi8f.webp",

  Username: "WhitePaper 白芷",
  Sign: "Ad Astra Per Aspera.",
  SocialLinks: [
    {
      icon: "ic-github",
      link: "https://github.com/WhitePaper233",
    },
    {
      icon: "ic-bilibili",
      link: "https://space.bilibili.com/22433608",
    },
    {
      icon: "ic-ncm",
      link: "https://music.163.com/#/user/home?id=125291648",
    },
  ],

  EnableBanner: true,

  // NOT IMPLEMENTED
  SlugGenerateMode: "HASH", // 'RAW' | 'HASH'

  HitokotoSettings: {
    // see: https://developer.hitokoto.cn/sentence/#%E8%AF%B7%E6%B1%82%E5%9C%B0%E5%9D%80
    url: "international.v1.hitokoto.cn",
    // see: https://developer.hitokoto.cn/sentence/#%E5%8F%A5%E5%AD%90%E7%B1%BB%E5%9E%8B-%E5%8F%82%E6%95%B0
    content_types: ["a", "b"],
  },
};
