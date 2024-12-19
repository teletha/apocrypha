---
title: 编码笔记：浅谈消除复杂if语句的几种技巧
description: 本文主要介绍了消除复杂if语句的几种技巧，包括卫语句、switch语句和责任链。
published: 2023-02-21
category: 编程
tags: ["编程", "设计模式", "Golang", "笔记"]
---

# 为什么要消除if

分支结构是基本的控制结构之一，在编写控制结构的代码时，一般使用的是 if 语句，但实际编码中复杂的条件关系会使得代码的可读性和可维护性急剧下降。

下面是一段我最开始构建的某段业务代码：

```go
// 这是一个格式化时间的工具函数的代码
package util

// 导入需要的包
import (
	"fmt"
	"time"
)

// 定义一些常量 增加下面代码的可读性
const (
	MINUTE    = 60
	HOUR      = 60 * MINUTE
	DAY       = 24 * HOUR
	THREE_DAY = 3  * DAY
)

// 工具函数
// @param Timestamp 需要格式化的时间戳
func FormatTime(Timestamp int64) string {
	// 计算出与当前时间的时间差
	timeDiff := time.Now().Unix() - Timestamp

	if timeDiff < THREE_DAY {
		if timeDiff < DAY {
			if timeDiff < HOUR {
				if timeDiff < MINUTE {
					return "刚刚"
				}
				return fmt.Sprintf("%d 分钟前", (timeDiff / MINUTE))
			}
			return fmt.Sprintf("%d 小时前", (timeDiff / HOUR))
		}
		return fmt.Sprintf("%d 天前", (timeDiff / DAY))
	} else {
		return time.Unix(Timestamp, 0).Format("2006-01-02")
	}
}
```

# 使用卫语句

这样的代码明显过于复杂，并不利于阅读与维护，但通过观察逻辑关系不难得出，上面展现的逻辑关系并非可以同时满足的（任何一个时间戳只会满足一个分支），这时候我们就可以通过 **卫语句 ( guard clause )** 简化代码。

使用卫语句很简单，只需要按照分支的满足条件的多少由多到少依次提取出来判断并return就可以。

比如上述例子中，最难以满足（需要满足的条件最多的）就是小于 1 分钟的时间戳的判定，所以优先提取出这一条，并类推，最终我们获得了以下的代码。

```go
func FormatTime(Timestamp int64) string {
	// 计算出与当前时间的时间差
	timeDiff := time.Now().Unix() - Timestamp

	if timeDiff < MINUTE {
		return "刚刚"
	}

	if timeDiff < HOUR {
		return fmt.Sprintf("%d 分钟前", (timeDiff / MINUTE))
	}

	if timeDiff < DAY {
		return fmt.Sprintf("%d 小时前", (timeDiff / HOUR))
	}

	if timeDiff < THREE_DAY {
		return fmt.Sprintf("%d 天前", (timeDiff / DAY))
	}

	return time.Unix(Timestamp, 0).Format("2006-01-02")
}
```

可以看到，这样改进代码之后可读性与可维护性高了很多，虽然违背了函数的 **单一出口原则**，但我认为为了代码的可维护性，这样的牺牲是值得的。

# 使用 switch 语句

当然，对于这段代码，我们可以使用字符串构造器和switch语句同时解决上述问题。

```go
// 导入需要的包
import (
	// ...
	"strings"
)

// 定义一些常量 增加下面代码的可读性
// ...

// 工具函数
// @param Timestamp 需要格式化的时间戳
func FormatTime(Timestamp int64) string {
	// 构造一个字符串构造器
	var strBuilder strings.Builder

	// 计算出与当前时间的时间差
	timeDiff := time.Now().Unix() - Timestamp

	// 使用构造器格式化时间
	switch {
		case timeDiff < MINUTE:
			strBuilder.WriteString("刚刚")
		case timeDiff < HOUR:
			strBuilder.WriteString(fmt.Sprint((timeDiff / MINUTE)))
			strBuilder.WriteString(" 分钟前")
		case timeDiff < DAY:
			strBuilder.WriteString(fmt.Sprint((timeDiff / HOUR)))
			strBuilder.WriteString(" 分钟前")
		case timeDiff < THREE_DAY:
			strBuilder.WriteString(fmt.Sprint((timeDiff / DAY)))
			strBuilder.WriteString(" 天前")
		default:
			strBuilder.WriteString(time.Unix(Timestamp, 0).Format("2006-01-02"))
	}

	// 返回构造器构造的字符串
	return strBuilder.String()
}
```

这样看来这段代码是不是舒服多了？

# 使用责任链

在我们的实际业务中，可能某一个情况需要的业务代码很多，或者条件很多甚至于分支过多，导致上述使用上述方法改写过后的代码仍然过于冗长，不便于维护，这时我们就可以考虑使用责任链。

将上述代码用责任链改写后的代码如下：

```go
// ...
// 定义规则接口
type rule interface {
	Check(timeDiff int64) string
}

// 定义责任链
type chain struct {
	timeStamp int64
	timeDiff  int64
	rules     []rule
}

func (c *chain) Build(timestamp int64) *chain {
	c.timeStamp = timestamp
	c.timeDiff = time.Now().Unix() - timestamp
	return c
}

func (c *chain) SetRules(rules ...rule) {
	c.rules = rules
}

func (c *chain) CheckAll() string {
	for _, rule := range c.rules {
		result := rule.Check(c.timeDiff)
		if result != "" {
			return result
		}
	}
	return time.Unix(c.timeStamp, 0).Format("2006-01-02")
}

// 定义各种情况的规则
type lessThanMinuteRule struct{}

func (r *lessThanMinuteRule) Check(timeDiff int64) string {
	if timeDiff < MINUTE {
		return "刚刚"
	}
	return ""
}

type lessThanHourRule struct{}

func (r *lessThanHourRule) Check(timeDiff int64) string {
	if timeDiff < HOUR {
		return fmt.Sprintf("%d 分钟前", (timeDiff / MINUTE))
	}
	return ""
}

type lessThanDayRule struct{}

func (r *lessThanDayRule) Check(timeDiff int64) string {
	if timeDiff < DAY {
		return fmt.Sprintf("%d 小时前", (timeDiff / HOUR))
	}
	return ""
}

type lessThanThreeDayRule struct{}

func (r *lessThanThreeDayRule) Check(timeDiff int64) string {
	if timeDiff < THREE_DAY {
		return fmt.Sprintf("%d 小时前", (timeDiff / HOUR))
	}
	return ""
}

// 工具函数
// @param Timestamp 需要格式化的时间戳
func FormatTime(Timestamp int64) string {
	var responsibilityChain chain
	responsibilityChain.Build(Timestamp).SetRules(
		&lessThanMinuteRule{},
		&lessThanHourRule{},
		&lessThanDayRule{},
		&lessThanThreeDayRule{},
	)
	return responsibilityChain.CheckAll()
}
```

如此一来，即使我们想增加新的格式化条件，也可以通过声明新的rule结构体并向责任链中添加条件来实现，可维护性极大地提升了。
