package com.graphbrain.eco.nodes

import com.graphbrain.eco.NodeType.NodeType
import com.graphbrain.eco.{Word, Contexts, Context}

abstract class ProgNode(val lastTokenPos: Int) {
  def ntype: NodeType

  def stringValue(ctxts: Contexts, ctxt: Context): String = "" // error
  def numberValue(ctxts: Contexts, ctxt: Context): Double = 0 // error
  def booleanValue(ctxts: Contexts, ctxt: Context): Boolean = false // error
  def phraseValue(ctxts: Contexts, ctxt: Context): Array[Word] = null // error

  protected def error(msg: String) = println(msg)

  protected def typeError() = error("type error")
}