import { YukinaConfig } from "../../yukina.config";
import CryptoJS from "crypto-js";

export function SlugToRealSlug(slug: string) {
  switch (YukinaConfig.SlugGenerateMode) {
    case "HASH": {
      const hash = CryptoJS.SHA256(slug);
      const hasedSlug = hash.toString(CryptoJS.enc.Hex).slice(0, 8);
      // console.debug(`Mapping ${slug} to ${hasedSlug}`);
      return hasedSlug;
    }
    case "RAW":
      return slug;
    default:
      return slug;
  }
}

export function GetIndexFromHash(hash: string, listLength: number): number {
  // 将8位哈希字符串转换为数字
  let hashValue = 0;
  for (let i = 0; i < hash.length; i++) {
    hashValue += hash.charCodeAt(i) * 31 ** (hash.length - 1 - i);
  }

  // 对列表长度取模以获得索引
  const index = hashValue % listLength;
  return index;
}
