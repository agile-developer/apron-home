package com.apron.home

import org.springframework.boot.fromApplication
import org.springframework.boot.with


fun main(args: Array<String>) {
	fromApplication<ApronHomeApplication>().with(TestcontainersConfiguration::class).run(*args)
}
