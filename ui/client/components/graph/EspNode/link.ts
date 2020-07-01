/* eslint-disable i18next/no-literal-string */
import * as joint from "jointjs"
import {Edge} from "../../../types"
import NodeUtils from "../NodeUtils"
import {arrowMarker} from "../arrowMarker"

const LINK_TEXT_COLOR = "#686868"
const LINK_COLOR = "#F5F5F5"

function makeLabels(label = "") {
  return label.length === 0 ? [] : [{
    position: 0.5,
    attrs: {
      rect: {
        ref: "text",
        refX: -6,
        refY: -6,
        refWidth: "100%",
        refHeight: "100%",
        refWidth2: 12,
        refHeight2: 12,
        stroke: LINK_TEXT_COLOR,
        fill: LINK_COLOR,
        strokeWidth: 1,
        rx: 5,
        ry: 5,
        cursor: "pointer",
      },
      text: {
        text: label,
        fontWeight: 300,
        fontSize: 10,
        fill: LINK_TEXT_COLOR,
      },
    },
  }]
}

export const defaultLink = new joint.dia.Link({
  markup: "<path class=\"connection\"/><path class=\"connection-wrap\"/><g class=\"marker-vertices\"/><g class=\"marker-arrowheads\"/><g class=\"link-tools\"/>",
  attrs: {
    ".connection": {
      markerEnd: `url(#${arrowMarker.attr("id")})`,
    },
    ".link-tools": {
      noExport: true,
    },
  },
})

export const makeLink = (edge: Edge) => {
  const edgeLabel = NodeUtils.edgeLabel(edge)
  const labels = makeLabels(edgeLabel)
  return defaultLink.clone()
    //TODO: some different way to create id? Must be deterministic and unique
    .prop("id", `${edge.from}-${edge.to}-${edgeLabel}`)
    .prop("source", {id: edge.from, port: "Out"})
    .prop("target", {id: edge.to, port: "In"})
    .prop("labels", labels)
    .prop("edgeData", edge)
    .prop("definitionToCompare", {edge})
}
