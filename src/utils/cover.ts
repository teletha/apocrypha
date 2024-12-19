import { readFileSync } from "node:fs";
import { parse } from "yaml";
import { GetIndexFromHash } from "./hash";

export function GetCoverURLForUnspecifiedEntry(HashID: string): string {
  const carouselImgs = readFileSync("data/carousel_imgs.yaml", "utf-8");
  const carouselImgsList = parse(carouselImgs) as string[];
  const index = GetIndexFromHash(HashID, carouselImgsList.length);
  return carouselImgsList[index];
}
